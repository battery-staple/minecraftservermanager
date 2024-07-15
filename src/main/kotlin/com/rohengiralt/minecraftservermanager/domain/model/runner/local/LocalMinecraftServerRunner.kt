package com.rohengiralt.minecraftservermanager.domain.model.runner.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.model.run.LogEntry
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.currentruns.CurrentRunRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJar
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.freeJar
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironmentSpec
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerCurrentRunRecordRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerPastRunRepository
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadOnlyMutexGuardedResource
import com.rohengiralt.minecraftservermanager.util.ifNull
import com.rohengiralt.minecraftservermanager.util.iff
import com.rohengiralt.minecraftservermanager.util.wrapWith
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess.ProcessMessage
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.time.Duration.Companion.seconds

private val localRunnerConfig = Config { addSpec(LocalRunnerSpec) }
    .from.env()

private object LocalRunnerSpec : ConfigSpec() {
    val domain by required<String>()
}


object LocalMinecraftServerRunner : MinecraftServerRunner, KoinComponent {
    override val uuid: UUID = UUID.fromString("d72add0d-4746-4b46-9ecc-2dcd868062f9") // Randomly generated, but constant
    override var name: String = "Local"
    override val domain: String = localRunnerConfig[LocalRunnerSpec.domain]

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val currentRuns: CurrentRunRepository by inject()
    private val currentRunRecordRepository: MinecraftServerCurrentRunRecordRepository by inject()
    private val runningProcesses: MutableMap<UUID, MinecraftServerProcess> = mutableMapOf()
    private val serversToEnvironmentsResource: ReadOnlyMutexGuardedResource<MutableMap<UUID, LocalMinecraftServerEnvironment>> =
        ReadOnlyMutexGuardedResource(mutableMapOf()) // TODO: make property on MinecraftServer

    private val serverDispatcher: MinecraftServerDispatcher by inject()
    private val serverJarResourceManager: MinecraftServerJarResourceManager by inject()
    private val serverContentDirectoryPathRepository: LocalMinecraftServerContentDirectoryRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
    init {
        try {
            logger.info("Archiving left over current runs")
            val runsToArchive = currentRunRecordRepository.getAllRecords()
                .map { record ->
                    MinecraftServerPastRun(
                        uuid = record.runUUID,
                        serverUUID = record.serverUUID,
                        runnerUUID = record.runnerUUID,
                        startTime = record.startTime,
                        stopTime = null,
                        log = getLatestLog(record.serverUUID) ?: emptyList() // Assumes that the current run record was the last one, TODO: alternative approach?
                    )
                }

            pastRunRepository.savePastRuns(runsToArchive)
            logger.info("Archived ${runsToArchive.size} left over current run(s)")
            currentRunRecordRepository.removeAllRecords()
        } catch (e: Throwable) {
            logger.error("Failed to archive left over current run(s), got error: {}", e.message)
        }
    }

    @Deprecated("Use prepareEnvironment instead", ReplaceWith("prepareEnvironment(server) != null"))
    override suspend fun initializeServer(server: MinecraftServer): Boolean =
        prepareEnvironment(server) != null

    override suspend fun prepareEnvironment(server: MinecraftServer): MinecraftServerEnvironment? = serversToEnvironmentsResource.use { serversToEnvironments ->
        if (server.uuid in serversToEnvironments) {
            return null // TODO: throw exception
        }

        val environmentUUID = UUID.randomUUID()

        logger.debug("Getting content directory for server {} in environment {}", server.name, environmentUUID)
        val contentDirectory =
            serverContentDirectoryPathRepository
                .getOrCreateContentDirectory(server.uuid)

        if (contentDirectory == null) {
            logger.error("Couldn't access content directory for server {} in environment {}", server.name, environmentUUID)
            return null
        }

        logger.trace("Getting jar for server {} in environment {}", server.name, environmentUUID)
        val jar = serverJarResourceManager.accessJar(server.version, environmentUUID)

        if (jar == null) {
            logger.error("Couldn't access server jar for server {} in environment {}", server.name, environmentUUID)
            return null
        }

        val newEnvironment = LocalMinecraftServerEnvironment(
            uuid = environmentUUID,
            serverUUID = server.uuid,
            serverName = server.name,
            contentDirectory = contentDirectory,
            jar = jar
        )

        assert(newEnvironment.serverUUID !in serversToEnvironments)
        serversToEnvironments[newEnvironment.serverUUID] = newEnvironment

        newEnvironment
    }

    @Deprecated("Use cleanupEnvironment instead")
    override suspend fun removeServer(server: MinecraftServer): Boolean {
        return serversToEnvironmentsResource.use { serversToEnvironments ->
            val environment = serversToEnvironments[server.uuid] ?: return true
            cleanupEnvironment(environment)
        }
    }

    override suspend fun cleanupEnvironment(environment: MinecraftServerEnvironment): Boolean = serversToEnvironmentsResource.use { serversToEnvironments ->
        require(environment is LocalMinecraftServerEnvironment)
        require(serversToEnvironments.containsValue(environment))
        
        logger.trace("Cleaning up environment {} from local runner", environment.uuid)

        val currentRun = environment.currentRun.value
        if (currentRun != null) {
            logger.trace("Server in environment {} is currently running. Stopping.", environment.uuid)
            val stopRunSuccess = try {
                stopRun(currentRun.uuid)
            } catch (e: IllegalArgumentException) {
                true // stopRun throws IllegalArgumentException if the run was already stopped
            }

            if (!stopRunSuccess) {
                logger.error("Failed to stop server in environment {}", environment.uuid)
                return false
            }
        }

        val contentDirectorySuccess =
            serverContentDirectoryPathRepository
                .deleteContentDirectory(environment.serverUUID)

        val jarSuccess =
            serverJarResourceManager
                .freeJar(environment.jar, environment.uuid)

        if (!contentDirectorySuccess) {
            logger.error("Couldn't remove server content directory for environment {}", environment.uuid)
            return false
        }

        if (!jarSuccess) {
            logger.error("Couldn't free server jar for environment {}", environment.uuid)
            return false
        }

        serversToEnvironments.remove(environment.serverUUID)
        true
    }

    @Deprecated("Use prepareEnvironment and then call runServer on the returned Environment")
    override suspend fun runServer(server: MinecraftServer, environmentOverrides: MinecraftServerRuntimeEnvironmentSpec): MinecraftServerCurrentRun? {
        // TODO: If server already running (in same environment?), noop
        val environment = environmentOverrides.run { // TODO: Should use other default, get from config?
            copy(
                port = port ?: MinecraftServerRuntimeEnvironmentSpec.Port(Port(25565u)), // TODO: portRepository.getNextAvailablePort()
                maxHeapSize = maxHeapSize ?: MinecraftServerRuntimeEnvironmentSpec.MaxHeapSize(2048u),
                minHeapSize = minHeapSize ?: MinecraftServerRuntimeEnvironmentSpec.MinHeapSize(1024u)
            )
        }
        assert(environment.port !== null && environment.maxHeapSize !== null && environment.minHeapSize !== null)
        environment.port!!; environment.maxHeapSize!!; environment.minHeapSize!! // Allows smart casts

        return prepareEnvironment(server)
            ?.runServer(
                environment.port.port,
                environment.maxHeapSize.memoryMB,
                environment.minHeapSize.memoryMB
            )
    }

    private suspend fun recordNewCurrentRun(run: MinecraftServerCurrentRun, process: MinecraftServerProcess) {
        currentRuns.addCurrentRun(run)
        runningProcesses[run.uuid] = process
        currentRunRecordRepository.addRecord(MinecraftServerCurrentRunRecord.fromCurrentRun(run))
    }

    override suspend fun stopRun(uuid: UUID): Boolean {
        val process = runningProcesses[uuid].ifNull {
            logger.trace("Cannot stop run {}, run not found", uuid)
            throw IllegalArgumentException("Run $uuid not found")
        }

        return process.stop()
    }

    override suspend fun stopRunByServer(serverUUID: UUID): Boolean {
        val run = getCurrentRunByServer(serverUUID) ?: return false
        return try {
            stopRun(run.uuid)
        } catch (e: IllegalArgumentException) {
            true // Run not found because server was already stopped.
        }
    }

    override suspend fun stopAllRuns(): Boolean =
        runningProcesses.all { (_, process) ->
            process.stop()
        }

    private suspend fun MinecraftServerProcess.stop(): Boolean {
        stop(softTimeout = 5.seconds, additionalForcibleTimeout = 5.seconds).ifNull { //TODO: No magic number timeout
            logger.error("Timed out while trying to stop run $uuid")
            return false
        }

        logger.trace("Successfully stopped run {}", uuid)
        return true
    }

    override suspend fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByUUID(uuid)

    override suspend fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByServer(serverUUID)

    override suspend fun getAllCurrentRuns(): List<MinecraftServerCurrentRun> =
        currentRuns.getAllCurrentRuns()

    override suspend fun getAllCurrentRunsFlow(server: MinecraftServer?): Flow<List<MinecraftServerCurrentRun>> =
        currentRuns.getCurrentRunsState(server)

    private fun MinecraftServerProcess.archiveOnEndJob(run: MinecraftServerCurrentRun): Job = coroutineScope.launch {
        waitForEnd()

        val endTime = Clock.System.now()
        logger.info("Current run ${run.uuid} ended at instant $endTime, about to archive")

        runningProcesses.remove(run.uuid)
        currentRuns.deleteCurrentRun(run.uuid)
        currentRunRecordRepository.removeRecord(run.uuid)

        logger.trace("Removed current run {}, about to save past run", run.uuid)
        try {
            logger.trace("Converting run to past run")
            val pastRun = run.toPastRun(endTime)
            logger.trace("Saving past run")
            pastRunRepository.savePastRun(pastRun)
        } catch (e: Throwable) {
            logger.error("Error archiving past run: $e")
        }

        logger.trace("Successfully archived run {}", run.uuid)
    }

    private suspend fun MinecraftServerProcess.waitForEnd() {
        output
            .filterIsInstance<ProcessMessage.ProcessEnd>()
            .firstOrNull()
    }

    private fun MinecraftServerCurrentRun.toPastRun(
        endTime: Instant = Clock.System.now()
    ) = MinecraftServerPastRun(
        uuid = uuid,
        serverUUID = serverUUID,
        runnerUUID = runnerUUID,
        startTime = startTime,
        stopTime = endTime,
        log = getLatestLog(serverUUID) ?: emptyList()
    )

    private fun getLatestLog(serverUUID: UUID): List<LogEntry>? {
        val contentDirectory = serverContentDirectoryPathRepository.getExistingContentDirectory(serverUUID) ?: return null

        val log = contentDirectory / "logs" / "latest.log" // TODO: Ensure works on all versions of Minecraft
        if (!log.exists()) return null

        return try {
            log.readLines()
        } catch (e: IOException) {
            null
        }
    }

    private class LocalMinecraftServerEnvironment( // TODO: Move to top level
        override val uuid: UUID,
        override val serverUUID: UUID,
        val serverName: String,
        val contentDirectory: Path,
        val jar: MinecraftServerJar
    ) : MinecraftServerEnvironment {
        override val runnerUUID: UUID = LocalMinecraftServerRunner.uuid

        override suspend fun runServer(
            port: Port,
            maxHeapSizeMB: UInt,
            minHeapSizeMB: UInt
        ): MinecraftServerCurrentRun? {
            // TODO: If server already running (in same environment?), noop

            if (!contentDirectory.exists()) { // not using notExists because that returns false if we can't tell whether the file exists
                logger.error("Couldn't access content directory for server {} in environment {}", serverName, uuid)
                return null
            }

            logger.trace("Getting jar for for server {} in environment {}", serverName, uuid)
            if (!jar.path.exists()) { // not using notExists because that returns false if we can't tell whether the file exists
                logger.error("Couldn't access jar for server {} in environment {}", serverName, uuid)
                return null
            }

            logger.info("Starting server $serverUUID in environment {}", uuid)
            val startTime = Clock.System.now()
            val process = serverDispatcher.runServer(
                name = serverName,
                jar = jar.path,
                contentDirectory = contentDirectory,
                maxSpaceMegabytes = maxHeapSizeMB,
                minSpaceMegabytes = minHeapSizeMB,
            ) ?: return null

            val newCurrentRun = MinecraftServerCurrentRun(
                uuid = UUID.randomUUID(),
                serverUUID = serverUUID,
                runnerUUID = LocalMinecraftServerRunner.uuid,
                environment = MinecraftServerRuntimeEnvironmentSpec( // TODO: Replace with something else
                    port = MinecraftServerRuntimeEnvironmentSpec.Port(port),
                    maxHeapSize = MinecraftServerRuntimeEnvironmentSpec.MaxHeapSize(maxHeapSizeMB),
                    minHeapSize = MinecraftServerRuntimeEnvironmentSpec.MinHeapSize(minHeapSizeMB)
                ),
                address = MinecraftServerAddress(
                    host = domain,
                    port = port
                ),
                startTime = startTime,
                input = process.input,
                interleavedIO = process.interleavedIO
                    .filterIsInstance<ProcessMessage.IO<*>>()
                    .map { it.content }
            )

            logger.trace("Recording new current run {} for server {} in environment {}", newCurrentRun.uuid, serverName, uuid)
            recordNewCurrentRun(newCurrentRun, process)

            logger.trace("Starting archive on end job for run {}", newCurrentRun.uuid)
            process.archiveOnEndJob(newCurrentRun)

            _currentRun.update { newCurrentRun }
            return newCurrentRun
        }

        private val _currentRun = MutableStateFlow<MinecraftServerCurrentRun?>(null)
        val currentRun: StateFlow<MinecraftServerCurrentRun?> = _currentRun

        private val logger = LoggerFactory.getLogger(this::class.java)
    }
}