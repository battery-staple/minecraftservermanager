package com.rohengiralt.minecraftservermanager.domain.service

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRunnerRepository
import com.rohengiralt.shared.serverProcess.ServerIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface WebsocketAPIService {
    /**
     * Returns a channel intended to back the websocket that sends and receives console messages.
     * Note that although the type system allows sending instances of [ServerIO.Output] to the returned [Channel],
     * it is part of the contract of this method that callers **must** only send instances of [ServerIO.Input]
     * to the channel.
     */
    suspend fun getRunConsoleChannel(runnerUUID: RunnerUUID, runUUID: RunUUID): Channel<ServerIO>?
//    fun getRunLogChannel(runnerId: UUID, runUUID: UUID): Channel<String>?  // Use FileWatcher

    /**
     * Returns a flow for a that sends a new [MinecraftServer] instance whenever the server
     * with UUID [serverUUID] changes.
     * If the server is deleted, sends `null`.
     */
    suspend fun getServerUpdatesFlow(serverUUID: ServerUUID): StateFlow<MinecraftServer?>

    /**
     * Returns a flow that sends a new [List] of [MinecraftServerCurrentRun]s whenever the
     * current runs of the server with UUID [serverUUID] change.
     * @return a flow of current run updates, or `null` if the server or runner could not be retrieved.
     */
    suspend fun getAllCurrentRunsFlow(serverUUID: ServerUUID): StateFlow<List<MinecraftServerCurrentRun>>?

    /**
     * Returns a flow that sends a new [List] of [MinecraftServer]s whenever servers
     * are added or deleted.
     */
    suspend fun getAllServersUpdatesFlow(): StateFlow<List<MinecraftServer>>
}

class WebsocketAPIServiceImpl : WebsocketAPIService, KoinComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    override suspend fun getRunConsoleChannel(runnerUUID: RunnerUUID, runUUID: RunUUID): Channel<ServerIO>? {
        val runner = runnerRepository.getRunner(runnerUUID) ?: return null
        val run = runner.getCurrentRun(runUUID) ?: return null

        val output = Channel<ServerIO>()
        coroutineScope.launch {
            run.interleavedIO.collect { ioMessage ->
                output.send(ioMessage)
            }
        }

        val input = Channel<ServerIO>()
        coroutineScope.launch {
            input.consumeEach { inputMessage ->
                assert(inputMessage is ServerIO.Input) { "Received non-input message ($inputMessage) as input"}
                run.input.send(inputMessage.text)
            }
        }

        return ServerIOChannel(input, output)
    }

    private class ServerIOChannel(
        private val input: SendChannel<ServerIO>,
        private val output: ReceiveChannel<ServerIO>
    ) : Channel<ServerIO>,
        SendChannel<ServerIO> by input,
        ReceiveChannel<ServerIO> by output

    override suspend fun getServerUpdatesFlow(serverUUID: ServerUUID): StateFlow<MinecraftServer?> =
        serverRepository.getServerUpdates(serverUUID)

    override suspend fun getAllCurrentRunsFlow(serverUUID: ServerUUID): StateFlow<List<MinecraftServerCurrentRun>>? {
        val server = runCatching { serverRepository.getServer(serverUUID) }.getOrNull() ?: return null
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return null

        return runner.getAllCurrentRunsFlow(server)
    }

    override suspend fun getAllServersUpdatesFlow(): StateFlow<List<MinecraftServer>> =
        serverRepository.getAllUpdates()

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
}