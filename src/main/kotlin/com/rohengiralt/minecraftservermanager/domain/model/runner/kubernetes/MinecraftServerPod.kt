package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes

import com.rohengiralt.minecraftservermanager.util.extensions.map.contains
import com.rohengiralt.minecraftservermanager.util.kubernetes.asRequest
import com.rohengiralt.minecraftservermanager.util.kubernetes.watch
import com.rohengiralt.minecraftservermanager.util.tryWithBackoff
import com.rohengiralt.shared.apiModel.ConsoleMessageAPIModel
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.PipingMinecraftServerProcess
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import kotlinx.atomicfu.atomic
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
@OptIn(ExperimentalCoroutinesApi::class)
class MinecraftServerPod(
    serverName: String,
    private val hostname: String,
    private val port: Int,
    private val podLabel: Pair<String, String>,
    private val token: MonitorToken,
) : PipingMinecraftServerProcess(serverName), KoinComponent {
    private val client: HttpClient by inject()
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json: Json by inject()
    private val kubeCore: CoreV1Api by inject()
    private val kubeClient: ApiClient by inject()
    private val logger = LoggerFactory.getLogger(MinecraftServerPod::class.java)

    private val state = MutableStateFlow<State>(State.Unknown)

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
            .onEach { if (it !is Frame.Text) logger.warn("Received non-text frame {}", it) }
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
            .map { json.decodeFromString<ConsoleMessageAPIModel.Output>(it) }
            .onCompletion { logger.debug("Incoming messages from pod for server {} ended", serverName) }

        messages.collect { message ->
            val outputFlow = when (message) {
                is ConsoleMessageAPIModel.Output.Log -> _stdOut
                is ConsoleMessageAPIModel.Output.ProcessError -> _stdError
            }

            ensureActive()
            outputFlow.emit(message.text)
        }
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

    /**
     * Updates [session] with a new WebSocket connection to the monitor
     * and resets [session] to null when the connection is closed.
     */
    context(CoroutineScope)
    private suspend fun MinecraftServerPod.newManagedSession() {
        val sessionNumber = nextSessionNumber.getAndIncrement()

        logger.debug("Creating new connection (#{}) to monitor at {}:{}", sessionNumber, hostname, port)
        val newSession = connectWithBackoff()
        logger.debug("Created new connection (#{}) to monitor at {}:{}", sessionNumber, hostname, port)

        session.value = newSession

        newSession.waitForSessionEnd()
        logger.debug("Ending connection (#{}) to monitor at {}:{}", sessionNumber, hostname, port)
        session.compareAndSet(newSession, null)
    }

    private val nextSessionNumber = atomic(1)

    /**
     * Establishes a WebSocket connection to the monitor.
     * May make multiple connection requests before finally succeeding.
     * If so, it will employ a backoff to prevent overloading the monitor with connection requests.
     * @return the new WebSocket session
     */
    context(CoroutineScope)
    private suspend fun MinecraftServerPod.connectWithBackoff(): DefaultClientWebSocketSession {
        tryWithBackoff(INITIAL_RECONNECT_DELAY, onRestart = ::logConnectFailure) { attempt ->
            logger.debug("Creating new connection (attempt {}) to monitor at {}:{}", attempt, hostname, port)
            return tryConnectToMonitor()
        }
    }

    /**
     * Logs that a connection attempt to a monitor failed
     * @param ex the exception that caused the failure
     * @param attempt the attempt on which the monitor failed
     */
    private fun logConnectFailure(ex: Exception, attempt: Int) {
        logger.warn("Failed to initialize connection (attempt {})", attempt, ex)
    }

    /**
     * Opens a WebSocket connection to the monitor instance.
     * @throws IOException if connection fails
     * @return the new WebSocket session
     */
    private suspend fun tryConnectToMonitor(): DefaultClientWebSocketSession = client.webSocketSession {
        url {
            protocol = URLProtocol.WS
            host = this@MinecraftServerPod.hostname
            port = this@MinecraftServerPod.port
            path("/io")
        }

        bearerAuth(token.asString())
    }

    init {
        // Update the state when the pod changes
        coroutineScope.launch {
            val currentPod =
                kubeClient
                    .watch { kubeCore.listNamespacedPod("default").asRequest() }
                    .map { pods ->
                        pods.firstOrNull { pod -> podLabel in pod.metadata.labels }
                    }
                    .distinctUntilChangedBy { pod -> pod?.metadata?.name }

            val currentState =
                currentPod
                    .map { if (it == null) State.Stopped else State.Running(it) }
                    .onEach { newState -> logger.trace("State changed to {}", newState) }

            // Update instance variable with the current state
            currentState.collect(state)
        }.logEnd("state")

        // Maintain a constant websocket connection, recreating when the last ends or when the pod is replaced
        coroutineScope.launch {
            state.collectLatest { state ->
                when (state) {
                    is State.Running -> withContext(Dispatchers.IO.limitedParallelism(1)) {
                        while (isActive) {
                            newManagedSession()
                        }
                    }

                    State.Stopped -> {
                        exitCode.complete(null) // TODO: get the actual exit code somehow
                        session.value?.close(CloseReason(
                            CloseReason.Codes.NORMAL, "Connection closed"
                        ))
                        session.value = null
                    }

                    State.Unknown -> { /* Wait until the state is something recognizable */ }
                }
            }
        }.logEnd("session")

        // Handle output of session
        coroutineScope.launch {
            session.filterNotNull().collectLatest { session ->
                session.handleIncoming()
            }
        }.logEnd("pipe")

        initIO()
    }

    companion object {
        private val INITIAL_RECONNECT_DELAY = 250.milliseconds
    }

    private sealed class State {
        data class Running(val pod: V1Pod) : State() {
            override fun toString() = "Running(pod=${pod.metadata.name})"
        }
        data object Stopped : State()
        data object Unknown : State()
    }
}