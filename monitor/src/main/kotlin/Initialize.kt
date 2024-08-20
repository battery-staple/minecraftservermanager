package com.rohengiralt.monitor

import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.slf4j.LoggerFactory
import java.io.File
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
    logger.info("Ensuring msm-app is running")
    ensureAppRunning()

    logger.info("Downloading minecraft server jar")
    val jar = downloadJar(tempJarPath)

    logger.info("Validating jar")
    ensureJarValid(jar)

    logger.info("Copying jar to correct path")
    tempJarPath.copyTo(jarPath)
    logger.info("Successfully copied jar")
}

/**
 * Where the jar is written while being downloaded and during validation.
 */
private val tempJarPath = dataDir / "minecraftserver-TEMP.jar"

/**
 * Ensures that [jar] was correctly downloaded.
 * If it was, returns normally; otherwise, throws an exception.
 */
private suspend fun ensureJarValid(jar: File) {
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

private val logger = LoggerFactory.getLogger("Initialization")