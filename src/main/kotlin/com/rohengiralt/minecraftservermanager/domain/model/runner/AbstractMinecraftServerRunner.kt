package com.rohengiralt.minecraftservermanager.domain.model.runner

import com.rohengiralt.minecraftservermanager.domain.model.run.*
import com.rohengiralt.minecraftservermanager.domain.model.server.*
import com.rohengiralt.minecraftservermanager.domain.repository.*
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess.ProcessMessage
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.seconds

/**
 * A useful class to inherit from for [MinecraftServerRunner] implementations.
 * Handles creating [MinecraftServerCurrentRun]s and [MinecraftServerPastRun]s when processes are created or end.
 * Also handles graceful recovery from abrupt application exits.
 * @param E the type of environment used by this runner
 * @param uuid the UUID of this runner
 * @param name the name of this runner
 * @param environments where to store the environments created by this runner
 */
abstract class AbstractMinecraftServerRunner<E : MinecraftServerEnvironment>(
    final override val uuid: RunnerUUID,
    final override var name: String,
    /**
     * Stores the environments created by this runner
     */
    val environments: EnvironmentRepository<E>
) : MinecraftServerRunner, KoinComponent {

    /**
     * Prepares all resources required to allow [server] to run
     * @return a newly provisioned environment, or null if setup failed
     */
    protected abstract suspend fun prepareEnvironment(server: MinecraftServer): E?

    /**
     * Deletes or marks for later deletion all resources belonging to [environment].
     * @return true if the environment was successfully cleaned up; false if cleanup failed.
     */
    protected abstract suspend fun cleanupEnvironment(environment: E): Boolean

    /**
     * Attempts to get the log stored by a particular run.
     * @return the log for a particular run, or null if retrieval fails
     */
    protected abstract suspend fun getLog(runRecord: MinecraftServerCurrentRunRecord): List<LogEntry>? // TODO: Include as part of MSProcess/Instance

    private val initializingRuns: InitializingRunRepository = InMemoryInitializingRunRepository()
    private val currentRuns: CurrentRunRepository = InMemoryCurrentRunRepository()

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val environmentsMutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val currentRunRecordRepository: MinecraftServerCurrentRunRecordRepository by inject() // TODO: Don't inject; should be per-instance, not global
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()

    /*
     * To prevent deadlocks, resource acquisition order MUST be:
     * 1. serversToEnvironmentsResource
     * 2. environmentsResource
     */

    init {
        registerInstance(this)
    }

    override suspend fun initializeServer(server: MinecraftServer): Boolean = environmentsMutex.withLock {
        logger.trace("Initializing server {} ('{}')", server.uuid, server.name)

        val existingEnvironment = environments.getEnvironmentByServer(server.uuid)
        if (existingEnvironment != null) {
            logger.trace("Cannot initialize server {}; already initialized with environment {}", server.uuid, existingEnvironment.uuid)
            throw IllegalArgumentException("Server ${server.uuid} already set up")
        }

        val newEnvironment = prepareEnvironment(server)
        if (newEnvironment == null) {
            logger.trace("Failed to prepare environment for server {}", server)
            return false
        }

        logger.trace("Recording environment {}", newEnvironment.uuid)
        environments.addEnvironment(newEnvironment)

        logger.trace("Successfully initialized server {}", server)
        return true
    }

    override suspend fun removeServer(server: MinecraftServer): Boolean = environmentsMutex.withLock {
        val environment = environments.getEnvironmentByServer(server.uuid) ?: return true

        val currentProcess = environment.currentProcess.value
        if (currentProcess != null) {
            logger.trace("Server in environment {} is currently running. Stopping.", environment.uuid)
            val stopRunSuccess = currentProcess.stop()

            if (!stopRunSuccess) {
                logger.error("Failed to stop server in environment {}", environment.uuid)
                return false
            }
        }

        val cleanupSuccess = cleanupEnvironment(environment)
        if (!cleanupSuccess) {
            logger.trace("Failed to clean up environment {}", environment.uuid)
            return false
        }

        val removalSuccess = environments.removeEnvironment(environment)
        if (!removalSuccess) {
            logger.trace("Failed to remove environment {}", environment.uuid)
            return false
        }

        return true
    }

    override suspend fun runServer(
        server: MinecraftServer,
        runtimeEnvironment: MinecraftServerRuntimeEnvironment,
    ): MinecraftServerCurrentRun? {
        // TODO: If server already running (in same environment?), noop
        val port = runtimeEnvironment.port ?: MinecraftServerRuntimeEnvironment.Port(Port(25565u))
        val maxHeapSize = runtimeEnvironment.maxHeapSize ?: MinecraftServerRuntimeEnvironment.MaxHeapSize(2048u)
        val minHeapSize = runtimeEnvironment.minHeapSize ?: MinecraftServerRuntimeEnvironment.MinHeapSize(1024u)

        logger.trace("Running server {} with {}", server.uuid, runtimeEnvironment)

        logger.trace("Getting environment for server {} in runner {}", server.uuid, uuid)
        val environment = environments.getEnvironmentByServer(server.uuid)
        if (environment == null) {
            logger.error("No environment exists for server {}.", server.uuid)
            return null
        }

        val runUUID = RunUUID(UUID.randomUUID())
        logger.trace("Marking new run {} as initializing for server {} in runner {}", runUUID, server.uuid, this.uuid)
        initializingRuns.addInitializingRun(MinecraftServerInitializingRun(
            uuid = runUUID,
            serverUUID = server.uuid,
            runnerUUID = this.uuid,
            environment.uuid
        ))

        logger.trace("Starting process for server {} in runner {}", server.uuid, uuid)
        val startTime = Clock.System.now()
        val process = environment.runServer(
            port = port.port,
            maxHeapSizeMB = maxHeapSize.memoryMB,
            minHeapSizeMB = minHeapSize.memoryMB
        ) ?: return null

        logger.trace("Creating new current run for server {} in runner {}", server.uuid, uuid)
        val newCurrentRun = MinecraftServerCurrentRun(
            uuid = runUUID,
            serverUUID = server.uuid,
            runnerUUID = uuid,
            environmentUUID = environment.uuid,
            runtimeEnvironment = runtimeEnvironment,
            address = MinecraftServerAddress(
                host = domain,
                port = port.port
            ),
            startTime = startTime,
            process = process
        )

        logger.trace("Recording new current run {} for server {} in runner {}", newCurrentRun.uuid, server.uuid, uuid)
        recordNewCurrentRun(newCurrentRun)

        logger.trace("Unmarking run {} as initializing for server {} in runner {}", runUUID, server.uuid, uuid)
        initializingRuns.deleteInitializingRun(runUUID)

        logger.trace("Starting archive on end job for run {} of server {} in runner {}", newCurrentRun.uuid, server.uuid, uuid)
        process.archiveOnEndJob(newCurrentRun)

        return newCurrentRun
    }

    private suspend fun recordNewCurrentRun(run: MinecraftServerCurrentRun) {
        currentRuns.addCurrentRun(run)
        currentRunRecordRepository.addRecord(MinecraftServerCurrentRunRecord.fromCurrentRun(run))
    }

    private fun MinecraftServerProcess.archiveOnEndJob(run: MinecraftServerCurrentRun): Job = coroutineScope.launch {
        waitForEnd()

        val endTime = Clock.System.now()
        logger.info("Current run ${run.uuid} ended at instant $endTime, about to archive")

        currentRuns.deleteCurrentRun(run.uuid)

        logger.trace("Removed current run {}, about to save past run", run.uuid)
        try {
            val record = currentRunRecordRepository.getRecord(run.uuid) ?: error("No record found")
            logger.trace("Converting run to past run")
            val pastRun = record.toPastRun(endTime)
            logger.trace("Saving past run")
            pastRunRepository.savePastRun(pastRun)
            logger.trace("Saved past run for current run {}, deleting record", run.uuid) // TODO: if this fails right before here, do we recover correctly?
            currentRunRecordRepository.removeRecord(run.uuid)
            logger.trace("Successfully archived run {}", run.uuid)
        } catch (e: Throwable) {
            logger.error("Error archiving past run: $e")
        }
    }

    private suspend fun MinecraftServerProcess.waitForEnd() {
        output
            .filterIsInstance<ProcessMessage.ProcessEnd>()
            .firstOrNull()
    }

    private suspend fun MinecraftServerCurrentRunRecord.toPastRun(
        endTime: Instant = Clock.System.now()
    ) = MinecraftServerPastRun(
        uuid = runUUID,
        serverUUID = serverUUID,
        runnerUUID = runnerUUID,
        startTime = startTime,
        stopTime = endTime,
        log = getLog(this) ?: emptyList()
    )

    private suspend fun MinecraftServerProcess.stop(): Boolean {
        try {
            stop(softTimeout = 5.seconds, additionalForcibleTimeout = 5.seconds) // TODO: No magic number timeout
        } catch (e: MinecraftServerProcess.StopFailed) {
            logger.error("Timed out while trying to stop run $uuid") // TODO: this uuid is wrong
            return false
        }

        logger.trace("Successfully stopped run {}", uuid) // TODO: Exit code
        return true
    }

    override suspend fun stopRun(uuid: RunUUID): Boolean {
        val run = currentRuns.getCurrentRunByUUID(uuid)
        if (run == null) {
            logger.trace("Cannot stop run {}; run not found", uuid)
            throw IllegalArgumentException("Run $uuid not found")
        }

        val environment = environments.getEnvironment(run.environmentUUID)
        if (environment == null) {
            logger.trace("Cannot stop run {}; environment {} not found", run.uuid, run.environmentUUID)
            return false
        }

        val process = environment.currentProcess.value

        if (process == null) { // Run just ended
            logger.trace("Cannot stop run {}, process not found", uuid)
            throw IllegalArgumentException("Process for run $uuid not found")
        }

        return process.stop()
    }

    override suspend fun stopRunByServer(uuid: ServerUUID): Boolean {
        val run = getCurrentRunByServer(uuid)

        if (run == null) {
            logger.trace("Cannot stop run for server {}; none found", uuid)
            return false
        }

        return try {
            stopRun(run.uuid)
        } catch (e: IllegalArgumentException) {
            true // Run not found because server was already stopped.
        }
    }

    override suspend fun stopAllRuns(): Boolean =
        environments
            .getAllEnvironments()
            .mapNotNull { environment -> environment.currentProcess.value }
            .all { process -> process.stop() }

    override suspend fun getAllInitializingRuns(): List<MinecraftServerInitializingRun> =
        initializingRuns.getAllInitializingRuns()

    override suspend fun getCurrentRun(uuid: RunUUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByUUID(uuid)

    override suspend fun getCurrentRunByServer(uuid: ServerUUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByServer(uuid)

    override suspend fun getAllCurrentRuns(): List<MinecraftServerCurrentRun> =
        currentRuns.getAllCurrentRuns()

    override suspend fun getAllCurrentRunsFlow(server: MinecraftServer): StateFlow<List<MinecraftServerCurrentRun>> =
        currentRuns.getCurrentRunsState(server)

    companion object {
        /**
         * Restores all the current runs belonging to any subclass that
         * were not handled when the application last shut down.
         */
        fun recoverCurrentRuns() = coroutineScope.launch {
            val jobs = recoverCurrentRunsJobs.value

            jobs.forEach { job ->
                val success = job.start()
                if (success) {
                    logger.trace("Successfully started recover current runs job {}", job)
                } else {
                    logger.trace("Failed to start recover current runs job {}", job)
                }
            }

            jobs.joinAll()
        }

        /**
         * Registers a subclass for current run handling.
         * Should be called exclusively in the initializer of [AbstractMinecraftServerRunner].
         */
        private fun registerInstance(instance: AbstractMinecraftServerRunner<*>) {
            recoverCurrentRunsJobs.getAndUpdate { jobs ->
                jobs + recoverCurrentRunsJob(instance)
            }
        }

        /**
         * Handles recovery of left over current runs for an instance of [AbstractMinecraftServerRunner]
         */
        private fun recoverCurrentRunsJob(instance: AbstractMinecraftServerRunner<*>): Job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                instance.logger.info("Archiving left over current runs")
                val stoppedRuns = mutableListOf<MinecraftServerPastRun>()
                val continuingRuns = mutableListOf<MinecraftServerCurrentRun>()

                val records = instance.currentRunRecordRepository.getAllRecords()
                instance.logger.trace("Found ${records.size} record(s) to recover")

                for (record in records) {
                    val env = instance.environments.getEnvironment(record.environmentUUID)
                    if (env == null) {
                        instance.logger.trace(
                            "Environment {} not found. Not archiving record for run {}.",
                            record.environmentUUID,
                            record.runUUID
                        )
                        continue
                    }

                    val currentProcess = env.currentProcess.value
                    val isStillRunning = currentProcess?.toRecord() == record.process
                    if (isStillRunning) {
                        check(currentProcess != null) // TODO: Remove (K2 fails to infer this)
                        instance.logger.trace("Restoring current run for record {}", record.runUUID)
                        continuingRuns.add(record.toCurrentRun(currentProcess))
                    } else {
                        instance.logger.trace("Archiving left over record {}", record.runUUID)
                        stoppedRuns.add(record.toPastRun(instance))
                    }
                }

                instance.logger.info("Adding ${continuingRuns.size} continuing run(s)")
                instance.currentRuns.addCurrentRuns(continuingRuns)
                instance.logger.info("Added ${continuingRuns.size} continuing run(s)")

                instance.logger.info("Archiving ${continuingRuns.size} left over current run(s)")
                instance.pastRunRepository.savePastRuns(stoppedRuns)
                stoppedRuns.forEach { instance.currentRunRecordRepository.removeRecord(it.uuid) }
                instance.logger.info("Archived ${stoppedRuns.size} left over current run(s)")
            } catch (e: Throwable) {
                instance.logger.error("Failed to archive/restore left over current run(s)", e)
            }
        }

        /**
         * Creates a [MinecraftServerPastRun] from a [MinecraftServerCurrentRunRecord]
         */
        private suspend fun MinecraftServerCurrentRunRecord.toPastRun(instance: AbstractMinecraftServerRunner<*>): MinecraftServerPastRun =
            MinecraftServerPastRun(
                uuid = runUUID,
                serverUUID = serverUUID,
                runnerUUID = runnerUUID,
                startTime = startTime,
                stopTime = null,
                log = instance.getLog(this) ?: emptyList()
            )

        /**
         * Creates a [MinecraftServerCurrentRun] from a [MinecraftServerCurrentRunRecord]
         */
        private fun MinecraftServerCurrentRunRecord.toCurrentRun(process: MinecraftServerProcess): MinecraftServerCurrentRun =
            MinecraftServerCurrentRun(
                uuid = runUUID,
                serverUUID = serverUUID,
                runnerUUID = runnerUUID,
                environmentUUID = environmentUUID,
                runtimeEnvironment = runtimeEnvironment,
                address = address,
                startTime = startTime,
                process = process
            )

        /**
         * A list of all the current run recovery jobs, one from each instance.
         */
        private val recoverCurrentRunsJobs: AtomicRef<List<Job>> = atomic(emptyList())

        private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val logger = LoggerFactory.getLogger(Companion::class.java)
    }
}
