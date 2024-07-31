package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes

import com.rohengiralt.shared.apiModel.ConsoleMessageAPIModel
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.PipingMinecraftServerProcess
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [MinecraftServerProcess] representing a server being run in a Kubernetes pod.
 * @param serverName the name of the server running in the pod
 * @param hostname the hostname of the pod's HTTP/WebSocket interface
 * @param port the port of the pod's HTTP/WebSocket interface
 * @param token the token that can be used to authenticate against the pod
 */
class MinecraftServerPod(
    serverName: String,
    private val hostname: String,
    private val port: Int,
    private val token: MonitorToken,
) : PipingMinecraftServerProcess(serverName), KoinComponent {
    private val client: HttpClient by inject()
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json: Json by inject()
    private val logger = LoggerFactory.getLogger(MinecraftServerPod::class.java)

    override suspend fun trySend(input: String) {
        val session = awaitSession()
        logger.debug("Sending '{}' to monitor at {} over websocket", input, hostname)
        session.sendSerialized(ConsoleMessageAPIModel.Input(input))
    }

    private val _stdOut: MutableSharedFlow<String> = MutableSharedFlow()
    override val stdOut: Flow<String> = _stdOut.asSharedFlow()
    private val _stdError: MutableSharedFlow<String> = MutableSharedFlow()
    override val stdError: Flow<String> = _stdError.asSharedFlow()

    /**
     * The code with which the pod's process exited
     */
    private val exitCode = CompletableDeferred<Int?>()
    override suspend fun waitForExit(): Int? = exitCode.await()

    override suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int? {
        TODO()
    }

    /**
     * The websocket session with the pod, used for sending and receiving messages.
     */
    private val session: MutableStateFlow<DefaultClientWebSocketSession?> = MutableStateFlow(null)

    /**
     * Suspends until a session is available, then returns that session
     */
    private suspend fun awaitSession() = this.session.filterNotNull().first()

    /**
     * Handles the incoming frames from the websocket
     */
    private suspend fun DefaultClientWebSocketSession.handleIncoming() {
        val messages = incoming
            .receiveAsFlow()
            .onEach { if (it !is Frame.Text) logger.warn("Received non-text frame {}", it) else logger.trace("Incoming message {} from server {}", it.readText(), serverName) }
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
            .map { json.decodeFromString<ConsoleMessageAPIModel.Output>(it)}
            .onCompletion { logger.debug("Incoming messages from pod for server {} ended", serverName) }

        messages.collect { message ->
            val outputFlow = when (message) {
                is ConsoleMessageAPIModel.Output.Log -> _stdOut
                is ConsoleMessageAPIModel.Output.ProcessError -> _stdError
            }

            outputFlow.emit(message.text)
        }
    }

    /**
     * Handles the closing of the websocket
     */
    private suspend fun DefaultClientWebSocketSession.handleEnd() {
        waitForSessionEnd()
        // if we've reached here, collection has ended which means the websocket has closed. TODO: better way to detect ending
        exitCode.complete(null) // TODO: actual exit code
    }

    /**
     * Suspends until the receiver ends.
     * This is distinct from [waitForExit], which waits for the exit code to exposed to [exitCode].
     * If the exit code is sent over the websocket prior to session closing, for instance, this could occur after.
     */
    private suspend fun DefaultClientWebSocketSession.waitForSessionEnd() {
        try {
            closeReason.await()
        } catch (e: IOException) {
            logger.warn("Failed to receive close reason for session {}", this, e)
        }
    }

    /**
     * Logs when this job ends, along with information about the cause.
     * @param name the name of job, to be included in the log message
     */
    private fun Job.logEnd(name: String) {
        invokeOnCompletion { cause ->
            when (cause) {
                null -> logger.trace("Ended $name job normally")
                is CancellationException -> logger.trace("Ended $name job due to cancellation")
                else -> logger.trace("Ended $name job exceptionally", cause)
            }
        }
    }

    init {

        // Maintain a constant websocket connection, recreating when the last ends
        coroutineScope.launch {
            var nextConnectionNumber = 1 // not atomic because no concurrent access
            val defaultBackoff = 250.milliseconds // TODO: extract into ExponentialBackoff
            var backoff = defaultBackoff
            while (isActive) {
                val connectionNumber = nextConnectionNumber++
                logger.debug("Creating new connection (#{}) to monitor at {}:{} after waiting for {}", connectionNumber, hostname, port, backoff)
                delay(backoff)
                val newSession = try {
                    client.webSocketSession {
                        url {
                            protocol = URLProtocol.WS
                            host = this@MinecraftServerPod.hostname
                            port = this@MinecraftServerPod.port
                            path("/io")
                        }

                        bearerAuth(token.asString())
                    }
                } catch (e: IOException) {
                    logger.warn("Failed to initialize connection (#{})", connectionNumber, e)
                    backoff *= 2
                    continue
                }

                session.value = newSession
                newSession.waitForSessionEnd()
                logger.debug("Ending connection (#{}) to monitor at {}:{}", connectionNumber, hostname, port)
                session.value = null
                backoff = defaultBackoff
            }
        }.logEnd("session")

        // handle output of session
        coroutineScope.launch {
            val session = awaitSession()
            launch {
                session.handleIncoming()
            }

            launch {
                session.handleEnd()
            }
        }.logEnd("pipe")

        initIO()
    }
}