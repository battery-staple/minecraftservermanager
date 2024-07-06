package com.rohengiralt.monitor

import com.rohengiralt.apiModel.ConsoleMessageAPIModel
import com.rohengiralt.monitor.plugins.configureSecurity
import com.rohengiralt.monitor.plugins.configureSockets
import com.rohengiralt.monitor.serverProcess.MinecraftServerDispatcher
import com.rohengiralt.monitor.serverProcess.MinecraftServerProcess
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.filterIsInstance
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.system.exitProcess

private val dataDir = Paths.get("/monitor")
private val jarPath = dataDir / "minecraftserver.jar"
private val rundataPath = dataDir / "rundata"

private const val port = 8080

private val logger = LoggerFactory.getLogger("Main")
fun main() {
    val serverDispatcher = MinecraftServerDispatcher()

    val minSpaceMb = System.getenv("minSpaceMB")?.toUIntOrNull() ?: error("minSpaceMB must be specified")
    val maxSpaceMb = System.getenv("maxSpaceMB")?.toUIntOrNull() ?: error("maxSpaceMB must be specified")
    val process = serverDispatcher.runServer(
        jar = jarPath,
        contentDirectory = rundataPath,
        minSpaceMegabytes = minSpaceMb,
        maxSpaceMegabytes = maxSpaceMb,
    ) ?: error("Failed to start Minecraft server process")

    @OptIn(DelicateCoroutinesApi::class) // This job should have the same lifetime as the app
    GlobalScope.launch(Dispatchers.IO) {
        process.output.filterIsInstance<MinecraftServerProcess.ProcessMessage.ProcessEnd>()
            .collect { endSignal ->
                logger.info("Server ended with code ${endSignal.code}. Exiting.")
                exitProcess(endSignal.code ?: 255)
            }
    }

    logger.info("Starting server on port {}", port)
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        configureSecurity()
        configureSockets()

        routing {
            get("/status") {
                call.respondText("Running")
            }

            webSocket("/io") {
                coroutineScope {
                    launch(Dispatchers.IO) {
                        call.application.environment.log.trace("Starting websocket output job")
                        process.output.filterIsInstance<MinecraftServerProcess.ProcessMessage.IO<*>>()
                            .collect { message ->
                                call.application.environment.log.trace("Sending {}", message.content)
                                sendSerialized<ConsoleMessageAPIModel>( // Type parameter is necessary for the "type" field
                                                                        // to be included in the serialized object
                                    ConsoleMessageAPIModel.fromServerIO(message.content)
                                )
                            }

                        close(CloseReason(CloseReason.Codes.NORMAL, "Server stopped")) // If we've reached here, the channel closed
                                                                                       // (i.e., the process has ended)
                        call.application.environment.log.trace("Console websocket output job ended for runner")
                    }

                    launch(Dispatchers.IO) {
                        call.application.environment.log.trace("Starting websocket input job")
                        incoming.consumeEach {
                            launch {
                                (it as? Frame.Text)?.let { frame ->
                                    call.application.environment.log.trace("Received {}", frame.readText())
                                    process.input.send(frame.readText())
                                } ?: call.application.environment.log.warn(
                                    "Received non-text frame with type {}",
                                    it.frameType
                                )
                            }
                        }
                        call.application.environment.log.trace("Console websocket input job ended")
                    }
                }
            }
        }
    }.start(wait = true)
}