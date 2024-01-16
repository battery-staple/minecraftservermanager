package com.rohengiralt.minecraftservermanager.domain.service

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerIO
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRunnerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

interface WebsocketAPIService {
    /**
     * Returns a channel intended to back the websocket that sends and receives console messages.
     * Note that although the type system allows sending instances of [ServerIO.Output] to the returned [Channel],
     * it is part of the contract of this method that callers **must** only send instances of [ServerIO.Input]
     * to the channel.
     */
    suspend fun getRunConsoleChannel(runnerId: UUID, runUUID: UUID): Channel<ServerIO>?
//    fun getRunLogChannel(runnerId: UUID, runUUID: UUID): Channel<String>?  // Use FileWatcher

    /**
     * Returns a flow for a that sends a new [MinecraftServer] instance whenever the server
     * with UUID [serverId] changes.
     * If the server is deleted, sends `null`.
     */
    suspend fun getServerUpdatesFlow(serverId: UUID): Flow<MinecraftServer?>

    /**
     * Returns a flow for a that sends a new [List] of [MinecraftServerCurrentRun]s instance whenever the
     * current runs of the server with UUID [serverId] change.
     */
    suspend fun getAllCurrentRunsFlow(serverId: UUID): Flow<List<MinecraftServerCurrentRun>>?
}

class WebsocketAPIServiceImpl : WebsocketAPIService, KoinComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    override suspend fun getRunConsoleChannel(runnerId: UUID, runUUID: UUID): Channel<ServerIO>? {
        val runner = runnerRepository.getRunner(runnerId) ?: return null
        val run = runner.getCurrentRun(runUUID) ?: return null

        val output = Channel<ServerIO>()
        coroutineScope.launch {
            run.interleavedIO.collect { ioMessage ->
                logger.trace("Piping {}", ioMessage)
                output.send(ioMessage)
            }
        }

        val input = Channel<ServerIO>()
        coroutineScope.launch {
            input.consumeEach { inputMessage ->
                assert(inputMessage is ServerIO.Input) { "Received non-input message ($inputMessage) as input"}
                logger.trace("Piping {}", inputMessage)
                run.input.send(inputMessage.text)
            }
        }

        return object : Channel<ServerIO>, SendChannel<ServerIO> by input, ReceiveChannel<ServerIO> by output {}
    }

    override suspend fun getServerUpdatesFlow(serverId: UUID): Flow<MinecraftServer?> { // TODO: Stateflow to remove the need to GET before websocket
        return serverRepository.getServerUpdates(serverId)
    }

    override suspend fun getAllCurrentRunsFlow(serverId: UUID): Flow<List<MinecraftServerCurrentRun>>? {
        val server = serverRepository.getServer(serverId) ?: return null
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return null

        return runner.getAllCurrentRunsFlow(server)
    }

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
    private val logger = LoggerFactory.getLogger(this::class.java)
}