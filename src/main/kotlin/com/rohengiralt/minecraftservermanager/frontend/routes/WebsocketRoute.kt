package com.rohengiralt.minecraftservermanager.frontend.routes

import com.rohengiralt.minecraftservermanager.domain.service.WebsocketAPIService
import com.rohengiralt.minecraftservermanager.util.routes.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseUUIDOrBadRequest
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject

fun Route.websockets() {
    val websocketAPIService: WebsocketAPIService by this@websockets.inject()

    route("/runs/{runId}") {
        webSocket("/console") {
            coroutineScope {
                val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()
                val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

                val runChannel = websocketAPIService.getRunChannel(runnerId = runnerUUID, runUUID = runUUID)
                    ?: throw NotFoundException()

                println("Opening Webhook")
                launch(Dispatchers.IO) {
                    runChannel.consumeAsFlow().collect {
                        outgoing.send(Frame.Text(it)) // TODO: handle process ending
                    } ?: return@launch close(CloseReason(CloseReason.Codes.NORMAL, "No running process"))
                }
                launch(Dispatchers.IO) {
                    incoming.consumeEach {
                        launch {
                            (it as? Frame.Text)?.let { frame ->
                                println("Received ${frame.readText()}")
                                runChannel.send(frame.readText()) ?: close(
                                    CloseReason(
                                        CloseReason.Codes.NORMAL,
                                        "No running process"
                                    )
                                )
                            } ?: println("Received non-text frame with type ${it.frameType}")
                        }
                    }
                }
            }
        }
    }
}