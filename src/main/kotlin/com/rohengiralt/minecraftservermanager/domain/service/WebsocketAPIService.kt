package com.rohengiralt.minecraftservermanager.domain.service

import com.rohengiralt.minecraftservermanager.domain.model.*
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
import java.util.*

interface WebsocketAPIService {
    fun getRunConsoleChannel(runnerId: UUID, runUUID: UUID): Channel<String>?
//    fun getRunLogChannel(runnerId: UUID, runUUID: UUID): Channel<String>?  // Use FileWatcher
    fun getServerUpdatesFlow(serverId: UUID): Flow<MinecraftServer?>
    suspend fun getAllCurrentRunsFlow(serverId: UUID): Flow<List<MinecraftServerCurrentRun>>?
}

class WebsocketAPIServiceImpl : WebsocketAPIService, KoinComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    override fun getRunConsoleChannel(runnerId: UUID, runUUID: UUID): Channel<String>? {
        val runner = runnerRepository.getRunner(runnerId) ?: return null
        val run = runner.getCurrentRun(runUUID) ?: return null

        val output = Channel<String>()
        coroutineScope.launch {
            run.output.collect(output::send)
        }

        val input = Channel<String>()
        coroutineScope.launch {
            input.consumeEach {
                println("got input $it")
                run.input.send(it)
            }
        }

        return object : Channel<String>, SendChannel<String> by input, ReceiveChannel<String> by output {}
    }

    override fun getServerUpdatesFlow(serverId: UUID): Flow<MinecraftServer?> { // TODO: Stateflow to remove the need to GET before websocket
        return serverRepository.getServerUpdates(serverId)
    }

    override suspend fun getAllCurrentRunsFlow(serverId: UUID): Flow<List<MinecraftServerCurrentRun>>? {
        val server = serverRepository.getServer(serverId) ?: return null
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return null

        return runner.getAllCurrentRunsFlow(server)
    }

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()
}