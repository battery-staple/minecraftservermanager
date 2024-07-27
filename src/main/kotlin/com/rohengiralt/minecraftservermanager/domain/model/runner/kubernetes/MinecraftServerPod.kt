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
import kotlin.time.Duration

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
    private val token: String,
) : PipingMinecraftServerProcess(serverName), KoinComponent {
    private val client: HttpClient by inject()
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun trySend(input: String) {
        val session = session.await()
        logger.debug("Sending '{}' to monitor at {} over websocket", input, hostname)
        session.sendSerialized(ConsoleMessageAPIModel.Input(input))
    }

    override suspend fun getStdOut(): Flow<String> {
        val incoming = incomingMessages.await()
        return incoming
            .filterIsInstance<ConsoleMessageAPIModel.Output.Log>()
            .map { message -> message.text }
    }

    override suspend fun getStdErr(): Flow<String> {
        val incoming = incomingMessages.await()
        return incoming
            .filterIsInstance<ConsoleMessageAPIModel.Output.ProcessError>()
            .map { message -> message.text }
    }

    override suspend fun waitForExit(): Int? {
        incomingMessages.await().collect()
        return null
    }

    override suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int? {
        TODO()
    }

    /**
     * The websocket session with the pod, used for sending and receiving messages.
     */
    private val session: Deferred<DefaultClientWebSocketSession> = coroutineScope.async {
        client.webSocketSession {
            url {
                protocol = URLProtocol.WS
                host = this@MinecraftServerPod.hostname
                port = this@MinecraftServerPod.port
                path("/io")
            }

            bearerAuth(token)
        }
    }

    /**
     * The parsed messages sent from the pod.
     */
    private val incomingMessages: Deferred<Flow<ConsoleMessageAPIModel.Output>> = coroutineScope.async {
        val session = session.await()

        val messages = session.incoming
            .receiveAsFlow()
            .onEach { if (it !is Frame.Text) logger.warn("Received non-text frame {}", it) }
            .filterIsInstance<Frame.Text>()
            .map { it.readText() }
            .map { json.decodeFromString<ConsoleMessageAPIModel.Output>(it)}
            .onCompletion { logger.debug("Incoming messages from pod for server {} ended", serverName) }

        messages
    }

    private val json: Json by inject()
    private val logger = LoggerFactory.getLogger(MinecraftServerPod::class.java)

    init {
        initIO()
    }
}