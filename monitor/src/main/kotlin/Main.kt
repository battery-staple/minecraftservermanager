package com.rohengiralt.monitor

import com.rohengiralt.monitor.plugins.configureSecurity
import com.rohengiralt.monitor.plugins.configureSockets
import com.rohengiralt.shared.apiModel.ConsoleMessageAPIModel
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Paths
import kotlin.io.path.div

private val dataDir = Paths.get("/monitor")
private val jarPath = dataDir / "minecraftserver.jar"
private val rundataPath = dataDir / "rundata"

private val logger = LoggerFactory.getLogger("Main")
fun main() {
    val serverDispatcher = MinecraftServerDispatcher()

    val process = serverDispatcher.runServer(
        name = name,
        jar = jarPath,
        contentDirectory = rundataPath,
        minSpaceMegabytes = minSpaceMb,
        maxSpaceMegabytes = maxSpaceMb,
    ) ?: error("Failed to start Minecraft server process")

    logger.info("Starting exit on end job")
    exitOnEnd(process)

    logger.info("Starting server on port {}", port)
    embeddedServer(CIO, port = port, host = "0.0.0.0") {
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

                                // Can't use sendSerialized in GraalVM because of reflection
                                send(Frame.Text(
                                    Json.encodeToString(ConsoleMessageAPIModel.fromServerIO(message.content))
                                ))
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