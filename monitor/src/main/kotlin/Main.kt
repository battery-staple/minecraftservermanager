package com.rohengiralt.monitor

import com.rohengiralt.monitor.plugins.configureSecurity
import com.rohengiralt.monitor.plugins.configureSockets
import com.rohengiralt.monitor.routing.processIOSocket
import com.rohengiralt.monitor.routing.status
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("Main")

fun main() {
    logger.info("Monitor start")

    if (!isInitialized) {
        logger.info("Initializing monitor")
        runBlocking {
            initialize()
        }
        logger.info("Successfully initialized monitor")
        exitProcess(0)
    }

    val serverDispatcher = MinecraftServerDispatcher()

    val process = serverDispatcher.runServer(
        name = name,
        jar = jarPath,
        contentDirectory = rundataPath,
        minSpaceMegabytes = minSpaceMB,
        maxSpaceMegabytes = maxSpaceMB,
    ) ?: error("Failed to start Minecraft server process")

    logger.info("Starting exit on end job")
    exitOnEnd(process)

    logger.info("Starting server on port {}", port)
    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        configureSecurity()
        configureSockets()

        routing {
            authenticate {
                status()
                processIOSocket(process)
            }
        }
    }.start(wait = true)
}