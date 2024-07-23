package com.rohengiralt.monitor

import com.rohengiralt.monitor.plugins.configureSecurity
import com.rohengiralt.monitor.plugins.configureSockets
import com.rohengiralt.monitor.routing.processIOSocket
import com.rohengiralt.monitor.routing.status
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import java.text.NumberFormat
import kotlin.io.path.div

private val dataDir = Paths.get("/monitor")
private val jarPath = dataDir / "minecraftserver.jar"
private val rundataPath = dataDir / "rundata"

private val logger = LoggerFactory.getLogger("Main")

fun main() {

    val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        followRedirects = false
        expectSuccess = false
    }

    runBlocking {
        logger.info("Ensuring msm-app is running")
        val response = client.get {
            url {
                protocol = URLProtocol.HTTP
                host = "msm-app.default.svc.cluster.local"
                port = 8080
                path("/ping")
            }
        }

        val body: String = response.body()
        if (body != "pong") {
            throw IllegalStateException("Unexpected response body: $body")
        }
    }

    runBlocking {
        val response = client.get {
            url {
                protocol = URLProtocol.HTTP
                host = "msm-app.default.svc.cluster.local"
                port = 8080
                path("/api/monitor/v1/jar")
            }

            bearerAuth(token)
        }

        logger.info("Status: ${response.status}")
        val downloadedJarChannel = response.bodyAsChannel()
        downloadedJarChannel.copyAndClose(jarPath.toFile().writeChannel())

        val fileSizeStr = NumberFormat.getInstance().format(
            jarPath.toFile().length()
        )
        logger.info("Received jar ($fileSizeStr B)")
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