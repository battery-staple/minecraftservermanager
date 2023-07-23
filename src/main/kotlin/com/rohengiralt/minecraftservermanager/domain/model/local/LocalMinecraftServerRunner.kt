package com.rohengiralt.minecraftservermanager.domain.model.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.infrastructure.LocalMinecraftServerDispatcher
import com.rohengiralt.minecraftservermanager.domain.model.*
import com.rohengiralt.minecraftservermanager.domain.model.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.local.currentruns.CurrentRunRepository
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.accessJar
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.freeJar
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.prepareJar
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
    private val runningProcesses: MutableMap<UUID, MinecraftServerProcess> = mutableMapOf()

    init {
        // TODO: Check for current runs in the repository (leftovers from before in case of unexpected program quit)
        // and archive them
    }

    override suspend fun initializeServer(server: MinecraftServer): Boolean {
        println("Getting content directory for server ${server.name}")
        val contentDirectorySuccess =
            serverContentDirectoryPathRepository
                .createContentDirectoryIfNotExists(server)

        if (!contentDirectorySuccess) {
            println("Couldn't access content directory")
            return false
        }

        println("Getting jar for server ${server.name}")
        val jarSuccess = serverJarResourceManager.prepareJar(server)

        if (!jarSuccess) {
            println("Couldn't create server jar")
            return false
        }

        return true
    }

    override suspend fun removeServer(server: MinecraftServer): Boolean {
        val contentDirectorySuccess =
            serverContentDirectoryPathRepository
                .deleteContentDirectory(server)

        val jarSuccess =
            serverJarResourceManager
                .freeJar(server)

        if (!contentDirectorySuccess) {
            println("Couldn't remove server content directory")
            return false
        }

        if (!jarSuccess) {
            println("Couldn't free server jar")
            return false
        }

        return true
    }

    override suspend fun runServer(server: MinecraftServer, environmentOverrides: MinecraftServerEnvironment): MinecraftServerCurrentRun? {
        // TODO: If server already running (in same environment?), noop
        val environment = environmentOverrides.run { // TODO: Should use other default, get from config?
            copy(
                port = port ?: MinecraftServerEnvironment.Port(Port(25565u)),
                maxHeapSize = maxHeapSize ?: MinecraftServerEnvironment.MaxHeapSize(2048u),
                minHeapSize = minHeapSize ?: MinecraftServerEnvironment.MinHeapSize(1024u)
            )
        }
        environment.port!!; environment.maxHeapSize!!; environment.minHeapSize!! // Allows smart casts

        println("Getting content directory for server ${server.name}")
        val contentDirectory =
            serverContentDirectoryPathRepository
                .getOrCreateContentDirectory(server)
                .ifNull {
                    println("Couldn't access content directory")
                    return null
                }

        println("Getting jar for server ${server.name}")
        val jar =
            serverJarResourceManager
                .accessJar(server)
                .ifNull {
                    println("Couldn't get server jar")
                    return null
                }

        println("Starting server ${server.name}")
        val startTime = Clock.System.now()
        val process = serverDispatcher.runServer(
            name = server.name,
            jar = jar,
            contentDirectory = contentDirectory,
            port = environment.port.port,
            maxSpaceMegabytes = environment.maxHeapSize.memoryMB,
            minSpaceMegabytes = environment.minHeapSize.memoryMB,
        ) ?: return null

        return MinecraftServerCurrentRun(
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
            output = process.output.filterIsInstance<MinecraftServerProcess.TextOutput>().map { it.text },
        )
            .also { run ->
                println("Adding new current run ${run.uuid}")
                currentRuns.addCurrentRun(run)
                runningProcesses[run.uuid] = process
            }
            .also { currentRun ->
                process.archiveOnEndJob(currentRun) // TODO: HOW DO I ARCHIVE CURRENT RUNS WHEN (e.g) JVM QUITS??? (edit: Maybe save current runs to database and archive if any current runs exist on startup)
            }
    }

    override suspend fun stopRun(uuid: UUID): Boolean =
        runningProcesses[uuid].ifNull {
            println("Cannot stop run $uuid, run not found")
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
        stop(5.seconds, 5.seconds).ifNull { //TODO: No magic number timeout
            println("Timed out while trying to stop run $uuid")
            return false
        }

        println("Successfully stopped run $uuid")
        return true
    }

    override fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByUUID(uuid)

    override fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun? =
        currentRuns.getCurrentRunByServer(serverUUID)

    override fun getAllCurrentRuns(): List<MinecraftServerCurrentRun> =
        currentRuns.getAllCurrentRuns()

    override suspend fun getAllCurrentRunsFlow(server: MinecraftServer?): Flow<List<MinecraftServerCurrentRun>> =
        currentRuns.getCurrentRunsFlow(server)

    private fun MinecraftServerProcess.archiveOnEndJob(run: MinecraftServerCurrentRun): Job = coroutineScope.launch {
        println("Started archive on end job")
        waitForEnd()

        val endTime = Clock.System.now()
        println("Current run ${run.uuid} ended at instant $endTime, about to archive")
        runningProcesses.remove(run.uuid)
        currentRuns.deleteCurrentRun(run.uuid)

        println("Removed current run ${run.uuid}, about to save past run")

        val success: Boolean = try {
            println("Getting past run repository")
            val prr = pastRunRepository
            println("Converting run to past run")
            val pastRun = run.toPastRun(endTime)
            println("Actually saving past run")
            prr.savePastRun(pastRun)
        } catch (e: Throwable) {
            println("Error archiving past run: $e")
            false
        }

        if (success) {
            println("Successfully archived run ${run.uuid}")
        } else {
            println("Couldn't archive run ${run.uuid}")
        }
    }

    private suspend fun MinecraftServerProcess.waitForEnd() {
        output
            .filterIsInstance<MinecraftServerProcess.OutputEnd>()
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
        log = getLog()
    )

    private fun MinecraftServerCurrentRun.getLog(): List<LogEntry> {
        val contentDirectory = serverContentDirectoryPathRepository.getExistingContentDirectory(serverUUID)
            ?: return emptyList() // TODO: Should distinguish between empty log and no log?

        val log = contentDirectory / "logs" / "latest.log" // TODO: Ensure works on all versions of Minecraft
        if (!log.exists()) return emptyList()

        return try {
            log.readLines()
        } catch (e: IOException) {
            emptyList()
        }
    }

    private val serverDispatcher: LocalMinecraftServerDispatcher by inject()
    private val serverJarResourceManager: MinecraftServerJarResourceManager by inject()
    private val serverContentDirectoryPathRepository: LocalMinecraftServerContentDirectoryRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()
    private val minecraftServerRepository: MinecraftServerRepository by inject()
}