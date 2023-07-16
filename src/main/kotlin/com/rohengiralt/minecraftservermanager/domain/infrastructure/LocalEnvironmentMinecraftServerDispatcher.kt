package com.rohengiralt.minecraftservermanager.domain.infrastructure

import com.rohengiralt.minecraftservermanager.domain.model.Port
import com.rohengiralt.minecraftservermanager.domain.model.local.MinecraftServerProcess
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.MinecraftServerJar
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
        println("Running Server with jar $jar in directory $contentDirectory on port $port")
        println("Ensuring server content directory exists")
        contentDirectory.createDirectories()

        println("Agreeing to EULA") // TODO: Prompt user to agree to EULA instead of doing it automatically
        contentDirectory.eulaFile // TODO: use Java 'properties' API
            .writeText("eula=true") // Does this need to be run every time?

        println("Starting process")
        val process = ProcessBuilder(javaExecutable, "-Xms${minSpaceMegabytes}M", "-Xmx${maxSpaceMegabytes}M", "-jar", jar.path.escapedAbsolutePathString())
            .run {
                val workDir = contentDirectory.toFile()
                println("Running jar with command ${this.command()} and working directory $workDir")
                directory(workDir)
                
                try {
                    start()
                } catch (e: IOException) {
                    null
                }
            } ?: return null

        println("Successfully ran server with process $process")
        return MinecraftServerProcess(name, process)
    }

    private val Path.eulaFile get() = this/"eula.txt"
    private fun Path.escapedAbsolutePathString() =
        absolutePathString().replace(" ", "\\ ")
}