package com.rohengiralt.monitor

import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Quits the entire monitor application when the given [MinecraftServerProcess] ends.
 */
fun exitOnEnd(process: MinecraftServerProcess) {
    @OptIn(DelicateCoroutinesApi::class) // This job should have the same lifetime as the app
    GlobalScope.launch(Dispatchers.IO) {
        process.output.filterIsInstance<MinecraftServerProcess.ProcessMessage.ProcessEnd>()
            .collect { endSignal ->
                logger.info("Server ended with code ${endSignal.code}. Exiting.")
                exitProcess(endSignal.code ?: 255)
            }
    }
}

private val logger = LoggerFactory.getLogger("exitOnEnd")