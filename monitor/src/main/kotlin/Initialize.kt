package com.rohengiralt.monitor

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import org.slf4j.LoggerFactory
import java.io.File
import java.text.NumberFormat
import kotlin.io.path.copyTo
import kotlin.io.path.div
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
    client.ensureAppRunning()

    logger.info("Downloading minecraft server jar")
    val jar = client.downloadJar()

    logger.info("Validating jar")
    client.ensureJarValid(jar)

    logger.info("Copying jar to correct path")
    tempJarPath.copyTo(jarPath)
    logger.info("Successfully copied jar")
}

/**
 * Ensures the main application is running.
 * If it is, completes normally; if not, throws an exception.
 */
private suspend fun HttpClient.ensureAppRunning() {
    val pingResponse = get {
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
 * @throws IOException if this fails
 */
private suspend fun HttpClient.downloadJar(): File {
    val jarResponse = get {
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
    downloadedJarChannel.copyAndClose(tempJarPath.toFile().writeChannel())

    val fileSizeStr = NumberFormat.getInstance().format(
        tempJarPath.toFile().length()
    )
    logger.debug("Received jar ($fileSizeStr B)")

    return tempJarPath.toFile()
}

/**
 * Where the jar is written while being downloaded and during validation.
 */
private val tempJarPath = dataDir / "minecraftserver-TEMP.jar"

/**
 * Ensures that [jar] was correctly downloaded.
 * If it was, returns normally; otherwise, throws an exception.
 */
private suspend fun HttpClient.ensureJarValid(jar: File) {
    val localFileHash = sha1(jar)
    logger.trace("Local SHA-1 hash: {}", localFileHash)

    val desiredHash = serverSha1()
    logger.trace("Server SHA-1 hash: {}", desiredHash)

    val isValid = localFileHash.asBytes() contentEquals desiredHash
    if (!isValid) {
        error("Downloaded server hash did not match intended")
    }
}

/**
 * Computes the SHA-1 hash of [jar]
 */
private fun sha1(jar: File): HashCode {
    @Suppress("DEPRECATION") // Intentionally using SHA-1
    val localFileHash = Files.asByteSource(jar).hash(Hashing.sha1())
    return localFileHash
}

/**
 * Returns the intended SHA-1 hash of the server jar.
 */
private suspend fun HttpClient.serverSha1(): ByteArray {
    val sha1Response = get {
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

private val logger = LoggerFactory.getLogger("Initialization")