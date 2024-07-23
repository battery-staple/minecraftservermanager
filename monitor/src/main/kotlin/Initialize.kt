package com.rohengiralt.monitor

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.slf4j.LoggerFactory
import java.text.NumberFormat
import kotlin.io.path.exists

/**
 * Whether the app is ready to run
 */
val isInitialized: Boolean get() = jarPath.exists()

/**
 * Prepares all resources necessary to run the app
 */
suspend fun initialize() {
    val client = HttpClient(CIO) {
        followRedirects = false
        expectSuccess = false
    }

    logger.info("Ensuring msm-app is running")
    val pingResponse = client.get {
        url {
            protocol = URLProtocol.HTTP
            host = "msm-app.default.svc.cluster.local"
            port = 8080
            path("/ping")
        }
    }

    val body: String = pingResponse.body()
    if (body != "pong") {
        throw IllegalStateException("Unexpected response body: $body")
    }

    val jarResponse = client.get {
        url {
            protocol = URLProtocol.HTTP
            host = "msm-app.default.svc.cluster.local"
            port = 8080
            path("/api/monitor/v1/jar")
        }

        bearerAuth(token)
    }

    logger.info("Status: ${jarResponse.status}")
    val downloadedJarChannel = jarResponse.bodyAsChannel()
    downloadedJarChannel.copyAndClose(jarPath.toFile().writeChannel())

    val fileSizeStr = NumberFormat.getInstance().format(
        jarPath.toFile().length()
    )
    logger.info("Received jar ($fileSizeStr B)")
}

private val logger = LoggerFactory.getLogger("Initialization")