package com.rohengiralt.minecraftservermanager.domain.model.runner.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.infrastructure.LocalMinecraftServerDispatcher
import com.rohengiralt.minecraftservermanager.domain.model.run.LogEntry
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.MinecraftServerProcess.ProcessMessage
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.currentruns.CurrentRunRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.accessJar
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.freeJar
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.prepareJar
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerCurrentRunRecordRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerPastRunRepository
import com.rohengiralt.minecraftservermanager.util.ifNull
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
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

    private val serverDispatcher: LocalMinecraftServerDispatcher by inject()
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

            if (pastRunRepository.savePastRuns(runsToArchive)) {
                logger.info("Archived ${runsToArchive.size} left over current run(s)")
                currentRunRecordRepository.removeAllRecords()
            } else {
                logger.error("Failed to archive left over current run(s)")
            }
        } catch (e: Throwable) {
            logger.error("Failed to archive left over current run(s), got error: {}", e.message)
        }
    }

    override suspend fun initializeServer(server: MinecraftServer): Boolean {
        logger.debug("Getting content directory for server ${server.name}")
        val contentDirectorySuccess =
            serverContentDirectoryPathRepository
                .createContentDirectoryIfNotExists(server)

        if (!contentDirectorySuccess) {
            logger.error("Couldn't access content directory")
            return false
        }

        logger.trace("Getting jar for server ${server.name}")
        val jarSuccess = serverJarResourceManager.prepareJar(server)

        if (!jarSuccess) {
            logger.error("Couldn't create server jar")
            return false
        }

        return true
    }

    override suspend fun removeServer(server: MinecraftServer): Boolean {
        logger.debug("Removing server ${server.name} from local runner")
        val contentDirectorySuccess =
            serverContentDirectoryPathRepository
                .deleteContentDirectory(server)

        val jarSuccess =
            serverJarResourceManager
                .freeJar(server)

        if (!contentDirectorySuccess) {
            logger.error("Couldn't remove server content directory")
            return false
        }

        if (!jarSuccess) {
            logger.error("Couldn't free server jar")
            return false
        }

        return true
    }

    override suspend fun runServer(server: MinecraftServer, environmentOverrides: MinecraftServerEnvironment): MinecraftServerCurrentRun? {
        // TODO: If server already running (in same environment?), noop
        val environment = environmentOverrides.run { // TODO: Should use other default, get from config?
            copy(
                port = port ?: MinecraftServerEnvironment.Port(Port(25565u)), // TODO: portRepository.getNextAvailablePort()
                maxHeapSize = maxHeapSize ?: MinecraftServerEnvironment.MaxHeapSize(2048u),
                minHeapSize = minHeapSize ?: MinecraftServerEnvironment.MinHeapSize(1024u)
            )
        }
        assert(environment.port !== null && environment.maxHeapSize !== null && environment.minHeapSize !== null)
        environment.port!!; environment.maxHeapSize!!; environment.minHeapSize!! // Allows smart casts

        logger.trace("Getting content directory for server ${server.name}")
        val contentDirectory =
            serverContentDirectoryPathRepository
                .getOrCreateContentDirectory(server)
                .ifNull {
                    logger.error("Couldn't access content directory")
                    return null
                }

        logger.trace("Getting jar for server ${server.name}")
        val jar =
            serverJarResourceManager
                .accessJar(server)
                .ifNull {
                    logger.error("Couldn't get server jar")
                    return null
                }

        logger.info("Starting server ${server.name}")
        val startTime = Clock.System.now()
        val process = serverDispatcher.runServer(
            name = server.name,
            jar = jar,
            contentDirectory = contentDirectory,
            port = environment.port.port,
            maxSpaceMegabytes = environment.maxHeapSize.memoryMB,
            minSpaceMegabytes = environment.minHeapSize.memoryMB,
        ) ?: return null

        val newCurrentRun = MinecraftServerCurrentRun(
            uuid = UUID.randomUUID(),
            serverUUID = server.uuid,
            runnerUUID = uuid,
            environment = environment,
            address = MinecraftServerAddress(
                host = domain,
                port = environment.port.port
            ),
            startTime = startTime,
            input = process.input,
            interleavedIO = process.interleavedIO
                .filterIsInstance<ProcessMessage.IO<*>>()
                .map { it.content }
        )

        logger.trace("Recording new current run {}", newCurrentRun.uuid)
        recordNewCurrentRun(newCurrentRun, process)

        logger.trace("Starting archive on end job for run {}", newCurrentRun.uuid)
        process.archiveOnEndJob(newCurrentRun)

        return newCurrentRun
    }

    private suspend fun recordNewCurrentRun(run: MinecraftServerCurrentRun, process: MinecraftServerProcess) {
        currentRuns.addCurrentRun(run)
        runningProcesses[run.uuid] = process
        currentRunRecordRepository.addRecord(MinecraftServerCurrentRunRecord.fromCurrentRun(run))
    }

    override suspend fun stopRun(uuid: UUID): Boolean =
        runningProcesses[uuid].ifNull {
            logger.trace("Cannot stop run {}, run not found", uuid)
            return false // TODO: Propagate that run was not found
        }.stop()

    override suspend fun stopRunByServer(serverUUID: UUID): Boolean {
        val run = getCurrentRunByServer(serverUUID) ?: return false
        return stopRun(run.uuid)
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
        val success: Boolean = try {
            logger.trace("Converting run to past run")
            val pastRun = run.toPastRun(endTime)
            logger.trace("Saving past run")
            pastRunRepository.savePastRun(pastRun)
        } catch (e: Throwable) {
            logger.error("Error archiving past run: $e")
            false
        }

        if (success) {
            logger.trace("Successfully archived run {}", run.uuid)
        } else {
            logger.error("Couldn't archive run ${run.uuid}")
        }
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
        val contentDirectory = serverContentDirectoryPathRepository.getExistingContentDirectory(serverUUID)
            ?: return null // TODO: Should distinguish between empty log and no log?

        val log = contentDirectory / "logs" / "latest.log" // TODO: Ensure works on all versions of Minecraft
        if (!log.exists()) return null

        return try {
            log.readLines()
        } catch (e: IOException) {
            null
        }
    }
}