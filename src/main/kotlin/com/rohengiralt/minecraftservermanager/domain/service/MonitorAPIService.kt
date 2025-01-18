package com.rohengiralt.minecraftservermanager.domain.service

import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRunnerRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * Contains all domain actions accessible by endpoints to the monitor microservice
 */
interface MonitorAPIService {
    /**
     * Gets the minecraft jar file for a particular container
     */
    suspend fun getJar(serverUUID: ServerUUID): File

    /**
     * Gets the sha1 hash of the minecraft jar file for a particular container.
     * @throws IOException if hashing fails
     */
    suspend fun getSHA1(serverUUID: ServerUUID): ByteArray

    /**
     * Gets the UUID of the current run (or still initializing) run of a particular server.
     */
    suspend fun getRunUUID(serverUUID: ServerUUID): RunUUID?
}

class MonitorAPIServiceImpl : MonitorAPIService, KoinComponent {
    override suspend fun getJar(serverUUID: ServerUUID): File { // TODO: real implementation!
        return File("/minecraftservermanager/local/jars/1.8.9---RELEASE.jar")
    }

    override suspend fun getSHA1(serverUUID: ServerUUID): ByteArray {
        val jar = getJar(serverUUID)

        return Files.asByteSource(jar).hash(checksumHash).asBytes()
    }

    override suspend fun getRunUUID(serverUUID: ServerUUID): RunUUID? {
        logger.debug("Getting run UUID for server {}", serverUUID)
        logger.trace("Finding server with uuid {}", serverUUID)
        val server = servers.getServer(serverUUID) ?: return null

        logger.trace("Finding runner for server {}", server)
        val runner = runners.getRunner(server.runnerUUID) ?: return null

        logger.trace("Finding run for server {} in runner {}", server, runner.uuid)
        val runs = runner.getAllInitializingRuns().asSequence() + runner.getAllCurrentRuns().asSequence()
        val run = runs.find { it.serverUUID == serverUUID }

        if (run == null) {
            logger.error("Could not find run for server {} in runner {}", serverUUID, runner.uuid)
            return null
        }

        return run.uuid
    }

    @Suppress("DEPRECATION") // SHA1 is good enough for a checksum
    private val checksumHash = Hashing.sha1()

    private val servers: MinecraftServerRepository by inject()
    private val runners: MinecraftServerRunnerRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
}