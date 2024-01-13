package com.rohengiralt.minecraftservermanager.domain.infrastructure

import com.rohengiralt.minecraftservermanager.domain.model.runner.local.MinecraftServerProcess
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJar
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

class LocalMinecraftServerDispatcher {
    init {
        // TODO: configure java versions, etc
    }

    private val javaExecutable: String = "java"

    fun runServer(name: String, jar: MinecraftServerJar, contentDirectory: Path, port: Port, minSpaceMegabytes: UInt, maxSpaceMegabytes: UInt): MinecraftServerProcess? { // TODO: MUST RESTRICT PARALLEL RUNS—concurrent manipulation of content directory almost certainly bad
        logger.debug("Running Server with jar {} in directory {} on port {}", jar, contentDirectory, port)

        logger.trace("Ensuring server content directory exists")
        contentDirectory.createDirectories()

        logger.trace("Agreeing to EULA") // TODO: Prompt user to agree to EULA instead of doing it automatically
        contentDirectory.eulaFile // TODO: use Java 'properties' API
            .writeText("eula=true") // Does this need to be run every time?

        logger.trace("Starting process")
        val process = ProcessBuilder(javaExecutable, "-Xms${minSpaceMegabytes}M", "-Xmx${maxSpaceMegabytes}M", "-jar", jar.path.escapedAbsolutePathString())
            .run {
                val workDir = contentDirectory.toFile()

                logger.trace("Setting jar working directory to {}", workDir)
                directory(workDir)
                
                try {
                    logger.trace(
                        "Attempting to run jar with command {} and working directory {}",
                        this.command(),
                        workDir
                    )
                    start().also {
                        logger.info("Successfully started running jar with command ${this.command()} and working directory $workDir")
                    }
                } catch (e: IOException) {
                    logger.error("Failed to run jar with command ${this.command()} and working directory $workDir")
                    null
                }
            } ?: return null

        logger.trace("Successfully ran server with process {}", process)
        return MinecraftServerProcess(name, process)
    }

    private val Path.eulaFile get() = this/"eula.txt"
    private fun Path.escapedAbsolutePathString() =
        absolutePathString().replace(" ", "\\ ")

    private val logger = LoggerFactory.getLogger(this::class.java)
}