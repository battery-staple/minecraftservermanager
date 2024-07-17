package com.rohengiralt.minecraftservermanager.domain.model.runner.local // To allow sealed interface inheritance

import com.rohengiralt.minecraftservermanager.domain.model.run.LogEntry
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.runner.AbstractMinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJar
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.freeJar
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess.ProcessMessage
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
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

    private val serverJarResourceManager: MinecraftServerJarResourceManager by inject()
    private val serverContentDirectoryPathRepository: LocalMinecraftServerContentDirectoryRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
}

private class LocalMinecraftServerEnvironment(
    override val uuid: UUID,
    override val serverUUID: UUID,
    val serverName: String,
    val contentDirectory: Path,
    val jar: MinecraftServerJar
) : MinecraftServerEnvironment, KoinComponent {
    override val runnerUUID: UUID = LocalMinecraftServerRunner.uuid

    override suspend fun runServer(
        port: Port,
        maxHeapSizeMB: UInt,
        minHeapSizeMB: UInt
    ): MinecraftServerProcess? {
        // TODO: If server already running (in same environment?), noop

        if (!contentDirectory.exists()) { // not using notExists because that returns false if we can't tell whether the file exists
            logger.error("Couldn't access content directory for server {} in environment {}", serverName, uuid)
            return null
        }

        logger.trace("Getting jar for for server {} in environment {}", serverName, uuid)
        if (!jar.path.exists()) { // not using notExists because that returns false if we can't tell whether the file exists
            logger.error("Couldn't access jar for server {} in environment {}", serverName, uuid)
            return null
        }

        logger.info("Starting server $serverUUID in environment {}", uuid)
        val process = serverDispatcher.runServer(
            name = serverName,
            jar = jar.path,
            contentDirectory = contentDirectory,
            maxSpaceMegabytes = maxHeapSizeMB,
            minSpaceMegabytes = minHeapSizeMB,
        ) ?: return null

        _currentProcess.update { process }
        return process
    }

    private val _currentProcess: MutableStateFlow<MinecraftServerProcess?> = MutableStateFlow(null)
    override val currentProcess: StateFlow<MinecraftServerProcess?> = _currentProcess

    private suspend fun handleProcessEnd(process: MinecraftServerProcess) {
        logger.trace("Waiting for process end")
        process.waitForEnd()
        logger.trace("Process ended; updating field")
        _currentProcess.compareAndSet(expect = process, update = null)
    }

    private suspend fun MinecraftServerProcess.waitForEnd() {
        try {
            output
                .cancellable()
                .filterIsInstance<ProcessMessage.ProcessEnd>()
                .firstOrNull()
            logger.trace("Ended waitForEnd job due to process end for process {} in environment {}", this, this@LocalMinecraftServerEnvironment.uuid)
        } catch (e: CancellationException) {
            logger.trace("Ended waitForEnd job due to cancellation for process {} in environment {}", this, this@LocalMinecraftServerEnvironment.uuid)
            throw e
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processEndScope = atomic<CoroutineScope?>(null)
    init {
        coroutineScope.launch {
            currentProcess.collect { process ->
                val oldScope: CoroutineScope?
                if (process != null) {
                    logger.trace("New process created; launching process end handler")
                    val newScope = CoroutineScope(Dispatchers.IO)
                    newScope.launch { handleProcessEnd(process) }

                    oldScope = processEndScope.getAndSet(newScope)
                    logger.trace("Cancelling old process end handler scope {}", oldScope)
                } else {
                    oldScope = processEndScope.value
                    logger.trace("Process removed; cancelling old process end handler scope {}", oldScope)
                }

                if (oldScope == null) {
                    logger.trace("No old process to cancel")
                } else {
                    oldScope.cancel()
                }
            }
        }
    }

    private val serverDispatcher: MinecraftServerDispatcher by inject()
    private val logger = LoggerFactory.getLogger(this::class.java)
}