package com.rohengiralt.minecraftservermanager.domain.model.runner.local

import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJar
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * An environment for running Minecraft servers on the same machine as the application.
 */
class LocalMinecraftServerEnvironment(
    override val uuid: EnvironmentUUID,
    override val serverUUID: ServerUUID,
    val serverName: String,
    val contentDirectory: Path,
    val jar: MinecraftServerJar
) : MinecraftServerEnvironment, KoinComponent {
    override val runnerUUID: RunnerUUID = LocalMinecraftServerRunner.uuid

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
                .filterIsInstance<MinecraftServerProcess.ProcessMessage.ProcessEnd>()
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