package com.rohengiralt.minecraftservermanager.domain.model.runner

import com.rohengiralt.minecraftservermanager.domain.model.run.*
import com.rohengiralt.minecraftservermanager.domain.model.server.*
import com.rohengiralt.minecraftservermanager.domain.repository.CurrentRunRepository
import com.rohengiralt.minecraftservermanager.domain.repository.EnvironmentRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerCurrentRunRecordRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerPastRunRepository
import com.rohengiralt.minecraftservermanager.util.ifNull
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess.ProcessMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
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
 */
abstract class AbstractMinecraftServerRunner<E : MinecraftServerEnvironment>(
    final override val uuid: RunnerUUID,
    final override var name: String,
) : MinecraftServerRunner, KoinComponent {

    /**
     * Prepares all resources required to allow [server] to run
     * @return a newly provisioned environment, or null if setup failed
     */
    protected abstract suspend fun prepareEnvironment(server: MinecraftServer): E?

    /**
     * Deletes or marks for later deletion all resources belonging to [environment].
     * @throws IllegalArgumentException if [environment] was created by a different runner
     * @return true if the environment was successfully cleaned up; false if cleanup failed.
     */
    protected abstract suspend fun cleanupEnvironment(environment: MinecraftServerEnvironment): Boolean

    /**
     * Attempts to get the log stored by a particular run.
     * @return the log for a particular run, or null if retrieval fails
     */
    protected abstract suspend fun getLog(runRecord: MinecraftServerCurrentRunRecord): List<LogEntry>? // TODO: Include as part of MSProcess/Instance

    /**
     * Stores the environments created by this runner
     */
    protected abstract val environments: EnvironmentRepository<E>

    private val logger = LoggerFactory.getLogger(this::class.java)

    private val environmentsMutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val currentRuns: CurrentRunRepository by inject()
    private val currentRunRecordRepository: MinecraftServerCurrentRunRecordRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()

    /*
     * To prevent deadlocks, resource acquisition order MUST be:
     * 1. serversToEnvironmentsResource
     * 2. environmentsResource
     */

    init {
        try {
            logger.info("Archiving left over current runs")
            val runsToArchive = runBlocking {
                currentRunRecordRepository.getAllRecords()
                    .map { record ->
                        MinecraftServerPastRun(
                            uuid = record.runUUID,
                            serverUUID = record.serverUUID,
                            runnerUUID = record.runnerUUID,
                            startTime = record.startTime,
                            stopTime = null,
                            log = getLog(record) ?: emptyList()
                        )
                    }
            }

            pastRunRepository.savePastRuns(runsToArchive)
            logger.info("Archived ${runsToArchive.size} left over current run(s)")
            currentRunRecordRepository.removeAllRecords()
        } catch (e: Throwable) {
            logger.error("Failed to archive left over current run(s), got error: {}", e.message)
        }
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
        environmentOverrides: MinecraftServerRuntimeEnvironment,
    ): MinecraftServerCurrentRun? {
        // TODO: If server already running (in same environment?), noop
        val runtimeEnvironment = environmentOverrides.run { // TODO: Should use other default, get from config?
            copy(
                port = port ?: MinecraftServerRuntimeEnvironment.Port(Port(25565u)), // TODO: portRepository.getNextAvailablePort()
                maxHeapSize = maxHeapSize ?: MinecraftServerRuntimeEnvironment.MaxHeapSize(2048u),
                minHeapSize = minHeapSize ?: MinecraftServerRuntimeEnvironment.MinHeapSize(1024u)
            )
        }
        assert(runtimeEnvironment.port !== null && runtimeEnvironment.maxHeapSize !== null && runtimeEnvironment.minHeapSize !== null)
        runtimeEnvironment.port!!; runtimeEnvironment.maxHeapSize!!; runtimeEnvironment.minHeapSize!! // Allows smart casts

        logger.trace("Running server {} with {}", server.uuid, runtimeEnvironment)

        val environment = environments.getEnvironmentByServer(server.uuid)
        if (environment == null) {
            logger.error("No environment exists for server {}.", server.uuid)
            return null
        }

        val startTime = Clock.System.now()
        val process = environment.runServer(
            runtimeEnvironment.port.port,
            runtimeEnvironment.maxHeapSize.memoryMB,
            runtimeEnvironment.minHeapSize.memoryMB
        ) ?: return null

        val newCurrentRun = MinecraftServerCurrentRun(
            uuid = RunUUID(UUID.randomUUID()),
            serverUUID = server.uuid,
            runnerUUID = uuid,
            environmentUUID = environment.uuid,
            runtimeEnvironment = runtimeEnvironment,
            address = MinecraftServerAddress(
                host = domain,
                port = runtimeEnvironment.port.port
            ),
            startTime = startTime,
            input = process.input,
            interleavedIO = process.interleavedIO
                .filterIsInstance<ProcessMessage.IO<*>>()
                .map { it.content }
        )

        logger.trace("Recording new current run {} for server {} in environment {}", newCurrentRun.uuid, server.name, uuid)
        recordNewCurrentRun(newCurrentRun)

        logger.trace("Starting archive on end job for run {}", newCurrentRun.uuid)
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
        stop(softTimeout = 5.seconds, additionalForcibleTimeout = 5.seconds).ifNull { //TODO: No magic number timeout
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
            logger.trace("Cannot stop run {}, run not found", uuid)
            throw IllegalArgumentException("Run $uuid not found")
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

    override suspend fun getCurrentRun(uuid: RunUUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByUUID(uuid)

    override suspend fun getCurrentRunByServer(uuid: ServerUUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByServer(uuid)

    override suspend fun getAllCurrentRuns(): List<MinecraftServerCurrentRun> =
        currentRuns.getAllCurrentRuns()

    override suspend fun getAllCurrentRunsFlow(server: MinecraftServer): Flow<List<MinecraftServerCurrentRun>> =
        currentRuns.getCurrentRunsState(server)
}