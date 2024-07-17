package com.rohengiralt.minecraftservermanager.domain.model.runner.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.model.run.LogEntry
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.runner.AbstractMinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.freeJar
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.repository.LocalEnvironmentRepository
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readLines

private val localRunnerConfig = Config { addSpec(LocalRunnerSpec) }
    .from.env()

private object LocalRunnerSpec : ConfigSpec() {
    val domain by required<String>()
}


object LocalMinecraftServerRunner : AbstractMinecraftServerRunner(
    uuid = UUID.fromString("d72add0d-4746-4b46-9ecc-2dcd868062f9"), // Randomly generated, but constant
    name = "Local"
) {
    override val domain: String = localRunnerConfig[LocalRunnerSpec.domain]

    override suspend fun prepareEnvironment(server: MinecraftServer): MinecraftServerEnvironment? {
        val environmentUUID = UUID.randomUUID()

        logger.debug("Getting content directory for server {} in environment {}", server.name, environmentUUID)
        val contentDirectory =
            serverContentDirectoryPathRepository
                .getOrCreateContentDirectory(server.uuid)

        if (contentDirectory == null) {
            logger.error("Couldn't access content directory for server {} in environment {}", server.name, environmentUUID)
            return null
        }

        logger.trace("Getting jar for server {} in environment {}", server.name, environmentUUID)
        val jar = serverJarResourceManager.accessJar(server.version, environmentUUID)

        if (jar == null) {
            logger.error("Couldn't access server jar for server {} in environment {}", server.name, environmentUUID)
            return null
        }

        val newEnvironment = LocalMinecraftServerEnvironment(
            uuid = environmentUUID,
            serverUUID = server.uuid,
            serverName = server.name,
            contentDirectory = contentDirectory,
            jar = jar
        )

        return newEnvironment
    }

    override suspend fun cleanupEnvironment(environment: MinecraftServerEnvironment): Boolean {
        require(environment is LocalMinecraftServerEnvironment)

        logger.trace("Cleaning up environment {} from local runner", environment.uuid)

        val contentDirectorySuccess =
            serverContentDirectoryPathRepository
                .deleteContentDirectory(environment.serverUUID)

        val jarSuccess =
            serverJarResourceManager
                .freeJar(environment.jar, environment.uuid)

        if (!contentDirectorySuccess) {
            logger.error("Couldn't remove server content directory for environment {}", environment.uuid)
            return false
        }

        if (!jarSuccess) {
            logger.error("Couldn't free server jar for environment {}", environment.uuid)
            return false
        }

        return true
    }

    override fun getLog(runRecord: MinecraftServerCurrentRunRecord): List<LogEntry>? { // Assumes that the current run record was the last one, TODO: alternative approach?
        val contentDirectory = serverContentDirectoryPathRepository.getExistingContentDirectory(runRecord.serverUUID) ?: return null

        val log = contentDirectory / "logs" / "latest.log" // TODO: Ensure works on all versions of Minecraft
        if (!log.exists()) return null

        return try {
            log.readLines()
        } catch (e: IOException) {
            null
        }
    }

    override val environments: LocalEnvironmentRepository by inject()

    private val serverJarResourceManager: MinecraftServerJarResourceManager by inject()
    private val serverContentDirectoryPathRepository: LocalMinecraftServerContentDirectoryRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
}