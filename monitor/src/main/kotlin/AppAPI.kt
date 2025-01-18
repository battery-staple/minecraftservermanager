package com.rohengiralt.monitor

import com.rohengiralt.shared.util.uuid.UUIDSerializer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.text.NumberFormat
import java.util.*

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
    val pingResponse = client.get(appApiRequest("ping", auth = true))

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
    val jarResponse = client.get(appApiRequest("jar"))

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
 * @throws IOException if getting the hash fails
 */
suspend fun serverSha1(): ByteArray {
    val sha1Response = client.get(appApiRequest("sha1"))
    logger.debug("SHA-1 status: {}", sha1Response.status)

    if (!sha1Response.status.isSuccess())
        throw IOException("SHA-1 request failed, got status ${sha1Response.status}")

    return sha1Response.readBytes()
}

/**
 * Returns the run UUID assigned to this instance
 * @throws IOException if getting the UUID fails
 */
suspend fun runUUID(): UUID {
    val runResponse = client.get(appApiRequest("run"))

    if (!runResponse.status.isSuccess())
        throw IOException("Run UUID request failed, got status ${runResponse.status}")

    val runBody: String = runResponse.body()
    return Json.decodeFromString(UUIDSerializer, runBody)
}

private fun appApiRequest(path: String, auth: Boolean = true): HttpRequestBuilder.() -> Unit = {
    url {
        protocol = URLProtocol.HTTP
        host = "msm-app.default.svc.cluster.local"
        port = 8080
        path("/api/monitor/v1/$path")
    }

    if (auth) {
        bearerAuth(token)
    }
}

private val logger = LoggerFactory.getLogger("AppAPI")