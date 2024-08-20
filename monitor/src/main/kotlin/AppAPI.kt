package com.rohengiralt.monitor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readBytes
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.text.NumberFormat

/**
 * The HTTP Client used for communicating with the main app
 */
private val client = HttpClient(CIO) {
    followRedirects = false
    expectSuccess = false
}

/**
 * Ensures the main application is running.
 * If it is, completes normally; if not, throws an exception.
 */
suspend fun ensureAppRunning() {
    val pingResponse = client.get {
        url {
            protocol = URLProtocol.HTTP
            host = "msm-app.default.svc.cluster.local"
            port = 8080
            path("/ping")
        }
    }

    if (!pingResponse.status.isSuccess()) {
        throw IOException("Ping failed, got response $pingResponse")
    }

    val body: String = pingResponse.body()
    if (body != "pong") {
        throw IOException("Unexpected response body: $body")
    }
}

/**
 * Downloads the minecraft server jar for this monitor.
 * @param target where the jar is downloaded to
 * @throws IOException if this fails
 */
suspend fun downloadJar(target: Path): File {
    val jarResponse = client.get {
        url {
            protocol = URLProtocol.HTTP
            host = "msm-app.default.svc.cluster.local"
            port = 8080
            path("/api/monitor/v1/jar")
        }

        bearerAuth(token)
    }

    logger.debug("Jar status: {}", jarResponse.status)
    val downloadedJarChannel = jarResponse.bodyAsChannel()
    downloadedJarChannel.copyAndClose(target.toFile().writeChannel())

    val fileSizeStr = NumberFormat.getInstance().format(
        target.toFile().length()
    )
    logger.debug("Received jar ($fileSizeStr B)")

    return target.toFile()
}

/**
 * Returns the intended SHA-1 hash of the server jar.
 */
suspend fun serverSha1(): ByteArray {
    val sha1Response = client.get {
        url {
            protocol = URLProtocol.HTTP
            host = "msm-app.default.svc.cluster.local"
            port = 8080
            path("/api/monitor/v1/sha1")
        }

        bearerAuth(token)
    }
    logger.debug("SHA-1 status: {}", sha1Response.status)

    if (!sha1Response.status.isSuccess())
        throw IOException("SHA-1 request failed, got status ${sha1Response.status}")

    return sha1Response.readBytes()
}

private val logger = LoggerFactory.getLogger("AppAPI")