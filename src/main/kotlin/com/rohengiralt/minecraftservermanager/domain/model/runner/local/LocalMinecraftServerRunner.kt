package com.rohengiralt.minecraftservermanager.domain.model.runner.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.model.run.LogEntry
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.runner.AbstractMinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryFactory
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.freeJar
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.repository.LocalEnvironmentRepository
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import kotlin.io.path.*

private val localRunnerConfig = Config { addSpec(LocalRunnerSpec) }
    .from.env()

private object LocalRunnerSpec : ConfigSpec() {
    val domain by required<String>()
}


class LocalMinecraftServerRunner(uuid: RunnerUUID) : AbstractMinecraftServerRunner<LocalMinecraftServerEnvironment>(
    uuid = uuid,
    name = "Local",
    environments = getKoin().get<LocalEnvironmentRepository>()
) {
    override val domain: String = localRunnerConfig[LocalRunnerSpec.domain]

    override suspend fun prepareEnvironment(server: MinecraftServer): LocalMinecraftServerEnvironment? {
        val environmentUUID = EnvironmentUUID(UUID.randomUUID())

        logger.debug("Getting content directory for server {} in environment {}", server.name, environmentUUID)
        val contentDirectory =
            contentDirectoryFactory.newContentDirectoryPath(server.uuid)

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
            runnerUUID = this.uuid,
            serverName = server.name,
            contentDirectory = contentDirectory,
            jar = jar
        )

        return newEnvironment
    }

    override suspend fun cleanupEnvironment(environment: MinecraftServerEnvironment): Boolean {
        require(environment is LocalMinecraftServerEnvironment)

        logger.trace("Cleaning up environment {} from local runner", environment.uuid)

        @OptIn(ExperimentalPathApi::class)
        val contentDirectorySuccess = try {
            environment.contentDirectory.deleteRecursively()
            true
        } catch (e: IOException) {
            logger.error("Couldn't remove server content directory for environment {}", environment.uuid, e)
            false
        }

        val jarSuccess =
            serverJarResourceManager
                .freeJar(environment.jar, environment.uuid)

        if (!jarSuccess) {
            logger.error("Couldn't free server jar for environment {}", environment.uuid)
        }

        return contentDirectorySuccess && jarSuccess
    }

    override suspend fun getLog(runRecord: MinecraftServerCurrentRunRecord): List<LogEntry>? { // Assumes that the current run record was the last one, TODO: alternative approach?
        val environment = environments.getEnvironmentByServer(serverUUID = runRecord.serverUUID) ?: return null

        val log = environment.contentDirectory / "logs" / "latest.log" // TODO: Ensure works on all versions of Minecraft
        if (!log.exists()) return null

        return try {
            log.readLines()
        } catch (e: IOException) {
            null
        }
    }

    private val serverJarResourceManager: MinecraftServerJarResourceManager by inject()
    private val contentDirectoryFactory: LocalMinecraftServerContentDirectoryFactory by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
}