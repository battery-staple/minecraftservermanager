package com.rohengiralt.minecraftservermanager.domain.service

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerPastRunRepository
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerRunnerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

interface WebsocketAPIService {
    fun getRunChannel(runnerId: UUID, runUUID: UUID): Channel<String>?
}

class WebsocketAPIServiceImpl : WebsocketAPIService, KoinComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    override fun getRunChannel(runnerId: UUID, runUUID: UUID): Channel<String>? {
        val runner = runnerRepository.getRunner(runnerId) ?: return null
        val run = runner.getCurrentRun(runUUID) ?: return null

        val output = Channel<String>()
        coroutineScope.launch {
//            run.output.collect {
//                println("got output $it")
//                output.send(it)
//            }
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

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()
}