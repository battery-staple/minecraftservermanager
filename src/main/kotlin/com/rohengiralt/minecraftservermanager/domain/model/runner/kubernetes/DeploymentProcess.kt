package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes

import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.resources.monitorLabel
import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.resources.monitorName
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.util.extensions.logger.logOnEnd
import com.rohengiralt.minecraftservermanager.util.extensions.map.contains
import com.rohengiralt.minecraftservermanager.util.forever
import com.rohengiralt.minecraftservermanager.util.kubernetes.asRequest
import com.rohengiralt.minecraftservermanager.util.kubernetes.restartDeployment
import com.rohengiralt.minecraftservermanager.util.kubernetes.scaleDeployment
import com.rohengiralt.minecraftservermanager.util.kubernetes.watch
import com.rohengiralt.minecraftservermanager.util.tryWithBackoff
import com.rohengiralt.shared.apiModel.ConsoleMessageAPIModel
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.rohengiralt.shared.serverProcess.PipingMinecraftServerProcess
import com.rohengiralt.shared.util.assertAllPropertiesNotNull
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.AppsV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.V1Pod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A [MinecraftServerProcess] representing a server being run in a Kubernetes deployment.
 * @param server the server being run in the deployment
 * @param hostname the hostname of the pod's HTTP/WebSocket interface
 * @param port the port of the pod's HTTP/WebSocket interface
 * @param token the token that can be used to authenticate against the pod
 */
class DeploymentProcess(
    private val server: MinecraftServer,
    private val hostname: String,
    private val port: Int,
    private val currentPod: StateFlow<V1Pod?>,
    private val podLabel: Pair<String, String>,
    private val monitorID: String,
    private val token: MonitorToken,
) : PipingMinecraftServerProcess(server.name), KoinComponent {
    /**
     * The current state of the connection to the deployment.
     */
    private val state = MutableStateFlow<State>(State.Connecting)

    override suspend fun trySend(input: String) {
        val connection = getConnectionOrThrow()

        logger.debug("Sending '{}' to monitor over connection {}", input, connection)
        connection.send(input)
    }

    /**
     * Returns the current websocket connection to the deployment.
     * If not connection exists, throws an [IOException].
     */
    private fun getConnectionOrThrow(): PersistentWebsocket {
        val currentState = state.value
        if (currentState !is State.Running) throw IOException("Server ${server.uuid} is not running")
        val connection = currentState.connection
        return connection
    }

    private val _stdOut: MutableSharedFlow<String> = MutableSharedFlow()
    override val stdOut: Flow<String> = _stdOut.asSharedFlow()
    private val _stdError: MutableSharedFlow<String> = MutableSharedFlow()
    override val stdError: Flow<String> = _stdError.asSharedFlow()

    public override suspend fun waitForExit(): Int? = state.filterIsInstance<State.Stopped>().first().exitCode

    override suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int? {
        val stopSuccess = kubeApps.scaleDeployment(monitorName(monitorID), "default", replicas = 0)
        if (!stopSuccess) throw MinecraftServerProcess.StopFailed

        return null
    }

    /**
     * Connects to the deployment.
     * @param restartOnFailure when set, if the deployment does not respond within [CONNECT_POD_TIMEOUT], restarts the pod and tries again.
     *                         When not set, requires that the deployment is already running.
     *                         If not set, a failure to connect after [CONNECT_POD_TIMEOUT] will throw a [ConnectionTimeoutException].
     */
    suspend fun connect(restartOnFailure: Boolean) {
        if (state.value != State.Connecting) throw IOException("Server ${server.uuid} is done connecting.")

        logger.trace("Opening new websocket")
        val socket = newConnection(restart=restartOnFailure)

        logger.trace("Awaiting websocket to connect")

        socket.await()

        logger.trace("Websocket connected, updating state")
        assert(state.value == State.Connecting) // TODO: prevent this assertion failing due to TOCTOU
        state.value = State.Running(socket)

        // Close connection on end
        coroutineScope.launch {
            val initialPod = currentPod.value

            // Wait for pod to end (and/or be replaced); skip if it already ended (initialPod == null)
            if (initialPod != null) {
                currentPod
                    .first { it?.metadata?.name != initialPod.metadata?.name }
            }

            logger.trace("Closing connection to server {}", server.uuid)
            state.value = State.Stopped(null)
            socket.close()
        }.also { logger.logOnEnd(it, "close connection") }
    }

    /**
     * Thrown when a connection to a deployment takes too long.
     */
    class ConnectionTimeoutException : IOException()

    /**
     * Connects to the deployment.
     * @param restart when set, if the deployment does not respond within [CONNECT_POD_TIMEOUT], restarts the pod and tries again.
     *                If not set, a failure to connect after [CONNECT_POD_TIMEOUT] will throw a [ConnectionTimeoutException].
     * @return the new websocket connection
     */
    private fun newConnection(restart: Boolean): PersistentWebsocket {
        val onConnectionTimeout = if (restart) restartOnTimeout else throwOnTimeout

        return newConnectionWithTimeout(onConnectionTimeout)
    }

    /**
     * Restarts the deployment when [CONNECT_POD_TIMEOUT] expires.
     */
    private val restartOnTimeout = PersistentWebsocket.TimeoutHandler(timeout = CONNECT_POD_TIMEOUT) { attempt ->
        logger.debug("Connection (#{}) took too long, restarting monitor {}", attempt, monitorID)
        kubeApps.restartDeployment(monitorName(monitorID), "default")
    }

    /**
     * Throws [ConnectionTimeoutException] when [CONNECT_POD_TIMEOUT] expires.
     */
    private val throwOnTimeout = PersistentWebsocket.TimeoutHandler(timeout = CONNECT_POD_TIMEOUT) { attempt ->
        logger.debug("Connection (#{}) to {} took too long, throwing.", attempt, monitorID)
        throw ConnectionTimeoutException()
    }

    /**
     * Connects to the deployment with a specified [PersistentWebsocket.TimeoutHandler].
     * @param onConnectionTimeout a timeout for if connecting takes too long
     */
    private fun newConnectionWithTimeout(onConnectionTimeout: PersistentWebsocket.TimeoutHandler?): PersistentWebsocket =
        PersistentWebsocket(_stdOut, _stdError, onConnectionTimeout) {
            url {
                protocol = URLProtocol.WS
                host = this@DeploymentProcess.hostname
                port = this@DeploymentProcess.port
                path("/io")
            }

            bearerAuth(token.asString())
        }

    override fun toRecord(): MinecraftServerProcess.Record = Record(podLabel)

    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val logger = LoggerFactory.getLogger(DeploymentProcess::class.java)

    init {
        assertAllPropertiesNotNull()
        initIO()
    }

    /**
     * The different possible states for this connection to be in.
     */
    private sealed class State {
        /**
         * A connection to the deployment is currently being established, but is not yet fully established.
         */
        data object Connecting : State()

        /**
         * The 'happy' state; the deployment is connected and methods like [send] will work immediately.
         */
        data class Running(val connection: PersistentWebsocket) : State()

        /**
         * The current run has ended.
         * This is the final state.
         * Once this state has been reached, it will never leave.
         */
        data class Stopped(val exitCode: Int?) : State()
    }

    companion object : KoinComponent {
        /**
         * A fake constructor, necessary because it might suspend
         * @param server the server being run in the deployment
         * @param hostname the hostname of the pod's HTTP/WebSocket interface
         * @param port the port of the pod's HTTP/WebSocket interface
         * @param token the token that can be used to authenticate against the pod
         */
        suspend operator fun invoke(
            server: MinecraftServer,
            hostname: String,
            port: Int,
            token: MonitorToken,
        ): DeploymentProcess {
            val monitorID = KubernetesRunner.getMonitorID(server.uuid)
            val podLabel = monitorLabel(monitorID)
            val currentPod = watchPod(podLabel)

            return DeploymentProcess(
                server = server,
                hostname = hostname,
                port = port,
                currentPod = currentPod,
                podLabel = podLabel,
                monitorID = monitorID,
                token = token
            )
        }

        /**
         * Starts a watch for the unique pod with label [label].
         * Requires that no more than one pod in the default namespace has the specified label.
         */
        private suspend fun watchPod(label: Pair<String, String>) =
            with(companionScope) {
                kubeClient
                    .watch { kubeCore.listNamespacedPod("default").asRequest() }
                    .map { pods ->
                        pods.firstOrNull { pod -> label in pod.metadata.labels }
                    }
                    .distinctUntilChangedBy { pod -> pod?.metadata?.name }
                    .stateIn(companionScope)
            }

        private val companionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val kubeClient: ApiClient by inject()
        private val kubeCore: CoreV1Api by inject()
        private val kubeApps: AppsV1Api by inject()

        /**
         * If connecting to the pod takes longer than this, the pod will be restarted.
         */
        private val CONNECT_POD_TIMEOUT: Duration = 20.seconds // TODO: SET BACK TO HIGHER VALUE!!!

        /**
         * A [SerializersModule] that knows how to serialize [Record].
         */
        val recordSerializer = SerializersModule {
            polymorphic(MinecraftServerProcess.Record::class) {
                subclass(Record::class)
            }
        }
    }

    // Deployments are uniquely identified by the label on the pod
    @JvmInline // Not actually inlined in usage
    @Serializable
    private value class Record(val podLabel: Pair<String, String>) : MinecraftServerProcess.Record
}

@OptIn(ExperimentalCoroutinesApi::class)
private class PersistentWebsocket(
    private val stdOut: MutableSharedFlow<String>,
    private val stdError: MutableSharedFlow<String>,
    private val sessionCreationTimeoutHandler: TimeoutHandler? = null,
    private val configureSession: HttpRequestBuilder.() -> Unit,
) : AutoCloseable, KoinComponent {
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: HttpClient by inject()
    private val json: Json by inject()
    private val logger = LoggerFactory.getLogger(PersistentWebsocket::class.java)

    /**
     * Waits for the connection to be established.
     */
    suspend fun await() {
        val currentState = state
            .filterNot { it is State.Connecting }
            .first()

        when (currentState) {
            is State.Connected -> { return /* Success */ }
            State.Closed -> throw ConnectionClosedException()
            is State.Failed -> throw currentState.exception
            State.Connecting -> error("Impossible")
        }
    }

    /**
     * Thrown when trying to await an already closed exception
     */
    class ConnectionClosedException : IOException()

    suspend fun send(input: String) {
        logger.trace("Preparing to send message, awaiting session")
        val session = awaitSession()

        session.sendSerialized(ConsoleMessageAPIModel.Input(input))
    }

    private suspend fun awaitSession(): DefaultClientWebSocketSession =
        state.filterIsInstance<State.Connected>().first().session

    override fun close() {
        coroutineScope.launch {
            val currentSession = (state.value as? State.Connected)?.session
            currentSession?.close(
                CloseReason(
                    CloseReason.Codes.NORMAL, "Connection closed"
                )
            )
        }
        coroutineScope.cancel()
        state.value = State.Closed
    }

    /**
     * The websocket session with the pod, used for sending and receiving messages.
     */
    private val state: MutableStateFlow<State> = MutableStateFlow(State.Connecting)

    /**
     * The number of the current session.
     * Each time the current session changes, this value is incremented.
     */
    private val currentSessionNumber = AtomicInteger(0)

    /**
     * The URL of the websocket, for logging purposes
     */
    private val logURL: String = HttpRequestBuilder().apply(configureSession).url.buildString()

    init {
        assertAllPropertiesNotNull()

        // Maintain a constant websocket connection, recreating when the last ends
        coroutineScope.launch {
            withContext(Dispatchers.IO.limitedParallelism(1)) {
                try {
                    while (isActive) {
                        val (session, sessionNum) = newSession()
                        state.value = State.Connected(session)
                        session.waitForSessionEnd()
                        session.handleEnd(sessionNum)
                    }
                } catch (e: Throwable) {
                    ensureActive()
                    logger.warn("Websocket connection failed with error", e)
                    state.value = State.Failed(e)
                }
            }
        }.also { logger.logOnEnd(it, "session") }

        // Handle output of session
        coroutineScope.launch {
            state.filterIsInstance<State.Connected>().map { it.session }.collectLatest { session ->
                session.handleIncoming()
            }
        }.also { logger.logOnEnd(it, "pipe") }
    }

    /**
     * Creates a new websocket session and updates the class to use it.
     * @return a pair of the new session to the number this session is.
     */
    context(CoroutineScope)
    private suspend fun newSession(): Pair<DefaultClientWebSocketSession, Int> {
        val sessionNumber = currentSessionNumber.incrementAndGet()

        logger.debug("Creating new connection (#{}) to {}", sessionNumber, logURL)
        val newSession = establishConnection()
        logger.debug("Created new connection (#{}) to {}", sessionNumber, logURL)

        return newSession to sessionNumber
    }

    /**
     * Handles the end of a session.
     */
    private fun DefaultClientWebSocketSession.handleEnd(sessionNumber: Int) {
        logger.debug("Ending connection (#{}) to {}", sessionNumber, logURL)
        state.compareAndSet(State.Connected(this), State.Connecting) // Automatically reconnect after session end
    }

    /**
     * Establishes a connection, retrying if necessary.
     * May make multiple connection requests before finally succeeding.
     * If so, it will employ a backoff to prevent overloading the monitor with connection requests.
     * If the monitor still doesn't respond after a timeout specified by [sessionCreationTimeoutHandler],
     * the timeout handler's callback will be called and connection will start again.
     */
    context(CoroutineScope)
    private suspend fun establishConnection(): DefaultClientWebSocketSession =
        sessionCreationTimeoutHandler.withTimeout {
            tryWithBackoff(INITIAL_RECONNECT_DELAY, onRestart = ::logConnectFailure) { attempt ->
                logger.debug("Creating new connection (attempt {}) to {}", attempt, logURL)
                tryCreateSession()
            }
        }

    /**
     * Logs that a connection attempt failed
     * @param ex the exception that caused the failure
     * @param attempt the attempt on which the connection failed
     */
    private fun logConnectFailure(ex: Exception, attempt: Int) {
        logger.warn("Failed to initialize connection (attempt {}) to {}", attempt, logURL, ex)
    }

    /**
     * Opens a new WebSocket session.
     * @throws IOException if session creation fails
     * @return the new WebSocket session
     */
    private suspend fun tryCreateSession(): DefaultClientWebSocketSession =
        client.webSocketSession(configureSession)

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
            .onCompletion { logger.debug("Incoming messages from pod ended") }

        messages.collect { message ->
            val outputFlow = when (message) {
                is ConsoleMessageAPIModel.Output.Log -> stdOut
                is ConsoleMessageAPIModel.Output.ProcessError -> stdError
            }

            ensureActive()
            outputFlow.emit(message.text)
        }
    }

    /**
     * Suspends until the receiver ends.
     */
    private suspend fun DefaultClientWebSocketSession.waitForSessionEnd() {
        try {
            closeReason.await()
        } catch (e: IOException) {
            logger.warn("Failed to receive close reason for session {}", this, e)
        }
    }

    override fun toString(): String = "PersistentWebsocket(url=$logURL, sessionNumber=${currentSessionNumber.get()})"

    private sealed interface State {
        data object Connecting : State
        data class Connected(val session: DefaultClientWebSocketSession) : State
        data object Closed : State
        data class Failed(val exception: Throwable) : State
    }

    /**
     * Specifies a timeout, after which [onTimeout] should be called.
     */
    data class TimeoutHandler(val timeout: Duration, val onTimeout: suspend (attempts: Int) -> Unit)

    companion object {
        /**
         * After a connection fails, wait this long before reconnecting the first time.
         */
        private val INITIAL_RECONNECT_DELAY = 250.milliseconds

        /**
         * Runs [block], cancelling and restarting it after the timeout specified by the reciever.
         *
         * @param block the block to run with the timeout. Must be cancellable.
         */
        context(CoroutineScope)
        private suspend inline fun <T> TimeoutHandler?.withTimeout(
            crossinline block: suspend () -> T
        ): T {
            if (this == null) return block()

            forever { i ->
                ensureActive()
                val timeoutAttempt = i + 1 // start from 1

                try {
                    return withTimeout(timeout) { block() }
                } catch (e: TimeoutCancellationException) {
                    onTimeout(timeoutAttempt)
                }
            }
        }
    }
}