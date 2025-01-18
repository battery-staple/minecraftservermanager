package com.rohengiralt.monitor

import com.rohengiralt.monitor.plugins.configureSecurity
import com.rohengiralt.monitor.plugins.configureSockets
import com.rohengiralt.monitor.routing.logRoute
import com.rohengiralt.monitor.routing.processIOSocket
import com.rohengiralt.monitor.routing.status
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
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

    val logFiles = LogFiles()

    logger.info("Getting run UUID")
    val runUUID = runBlocking { runUUID() }
    logger.info("Setting up log files")
    val logFile = logFiles.create(runUUID)

    val serverLogFile = rundataPath / "logs" / "latest.log" // TODO: support versions <1.6.4 with server.log: https://www.hosthorde.com/clients/index.php?rp=/knowledgebase/44/Understanding-Minecraft-server-log-files.html
    logger.info("Setting up symlink from server.log file ($serverLogFile) to run log file ($logFile)")
    serverLogFile.createParentDirectories()
    serverLogFile.deleteIfExists()
    serverLogFile.createSymbolicLinkPointingTo(logFile)

    val serverDispatcher = MinecraftServerDispatcher()

    logger.info("Starting server process")
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
                logRoute(logFiles)
            }
        }
    }.start(wait = true)
}