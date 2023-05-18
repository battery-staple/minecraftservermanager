package com.rohengiralt.minecraftservermanager.domain.model.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.infrastructure.LocalMinecraftServerDispatcher
import com.rohengiralt.minecraftservermanager.domain.model.*
import com.rohengiralt.minecraftservermanager.util.ifNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.time.Duration.Companion.seconds

object LocalMinecraftServerRunner : MinecraftServerRunner, KoinComponent {
    override val uuid: UUID = UUID.fromString("d72add0d-4746-4b46-9ecc-2dcd868062f9") // Randomly generated, but constant
    override var name: String = "Local"
    override val domain: String = "home.translatorx.org" //TODO: Customizable (config?)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val currentRuns: MutableList<MinecraftServerCurrentRunWithMetadata> = mutableListOf()

    override suspend fun initializeServer(server: MinecraftServer) {
        TODO("Not yet implemented")
    }

    override suspend fun removeServer(server: MinecraftServer) {
        TODO("Not yet implemented")
    }

    override suspend fun runServer(server: MinecraftServer, environmentOverrides: MinecraftServerEnvironment): MinecraftServerCurrentRun? {
        val environment = environmentOverrides.run { // TODO: Should use other default, get from config?
            copy(
                port = port ?: MinecraftServerEnvironment.Port(Port(25565u)),
                maxHeapSize = maxHeapSize ?: MinecraftServerEnvironment.MaxHeapSize(2048u),
                minHeapSize = minHeapSize ?: MinecraftServerEnvironment.MinHeapSize(1024u)
            )
        }
        environment.port!!; environment.maxHeapSize!!; environment.minHeapSize!! // Allows smart casts

        val contentDirectory = with(serverContentDirectoryPathRepository) {
            getContentDirectoryPath(server) ?: saveContentDirectoryPath(
                server = server,
                path = serverContentDirectoryPathFactory.newContentDirectoryPath(server) ?: return null
            )
        }

        val jar = serverJarRepository
            .getJar(server.version)
            .ifNull {
                val new = serverJarFactory.newJar(server.version)
                serverJarRepository.saveJar(new)?.let { return@ifNull it }

                println("Couldn't save new jar; defaulting to unsaved")
                new
            }/*.let { jar ->
                jar.copy(
                    path = jar.path.moveTo(contentDirectory / "${jar.path.fileName}")
                )
            }*/

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
            serverId = server.uuid,
            runnerId = uuid,
            environment = environment,
            address = MinecraftServerAddress(
                host = domain,
                port = environment.port.port
            ),
            startTime = startTime,
            input = process.input,
            output = process.output.filterIsInstance<MinecraftServerProcess.TextOutput>().map { it.text },
        )
            .also {
                currentRuns.add(MinecraftServerCurrentRunWithMetadata(it, process))
            }
            .also { currentRun ->
                process.archiveOnEndJob(currentRun) // TODO: HOW DO I ARCHIVE CURRENT RUNS WHEN (e.g) JVM QUITS??? (edit: Maybe save current runs to database and archive if any current runs exist on startup)
            }
    }

    override suspend fun stopRun(uuid: UUID): Boolean {
        currentRuns.find { it.run.uuid == uuid }?.process?.stop(5.seconds) ?: return false //TODO: No magic number timeout
        return true
    }

    override fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun? =
        currentRuns.find { it.run.uuid == uuid }?.run

    override fun getAllCurrentRuns(server: MinecraftServer?): List<MinecraftServerCurrentRun> =
        currentRuns
            .asSequence()
            .map { it.run }
            .let { runs ->
                if (server != null)
                    runs.filter { it.serverId == server.uuid }
                else runs
            }
            .toList()


    private fun MinecraftServerProcess.archiveOnEndJob(run: MinecraftServerCurrentRun) = coroutineScope.launch {
        output
            .filterIsInstance<MinecraftServerProcess.OutputEnd>()
            .first() // TODO: Is this logic here the correct way to wait until the end of the flow?

        currentRuns.removeIf { it.run == run }
        if (!pastRunRepository.savePastRun(run.toPastRun())) {
            println("Couldn't archive run $run")
        }
    }

    private suspend fun MinecraftServerCurrentRun.toPastRun(
        endTime: Instant = Clock.System.now()
    ) = MinecraftServerPastRun(
        uuid = uuid,
        serverId = serverId,
        runnerId = runnerId,
        startTime = startTime,
        stopTime = endTime,
        log = output.toList()
    )

    private val serverDispatcher: LocalMinecraftServerDispatcher by inject()
    private val serverJarRepository: MinecraftServerJarRepository by inject()
    private val serverJarFactory: MinecraftServerJarFactory by inject()
    private val serverContentDirectoryPathRepository: LocalMinecraftServerContentDirectoryPathRepository by inject()
    private val serverContentDirectoryPathFactory: LocalMinecraftServerContentDirectoryPathFactory by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()

    private data class MinecraftServerCurrentRunWithMetadata(
        val run: MinecraftServerCurrentRun,
        val process: MinecraftServerProcess
    )
}