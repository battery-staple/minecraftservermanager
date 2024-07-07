package com.rohengiralt.monitor.routing

import com.rohengiralt.shared.apiModel.ConsoleMessageAPIModel
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
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

/**
 * The route `/io`, which hosts a websocket that pipes the input and output of the running Minecraft server process.
 */
fun Routing.processIOSocket(process: MinecraftServerProcess) {
    webSocket("/io") {
        coroutineScope {
            launch(Dispatchers.IO) {
                call.application.environment.log.trace("Starting websocket output job")
                process.output.filterIsInstance<MinecraftServerProcess.ProcessMessage.IO<*>>()
                    .collect { message ->
                        call.application.environment.log.trace("Sending {}", message.content)

                        // Can't use sendSerialized in GraalVM because of reflection
                        send(
                            Frame.Text(
                                Json.encodeToString(ConsoleMessageAPIModel.fromServerIO(message.content))
                            )
                        )
                    }

                close(
                    CloseReason(
                        CloseReason.Codes.NORMAL,
                        "Server stopped"
                    )
                ) // If we've reached here, the channel closed
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