package com.rohengiralt.minecraftservermanager.frontend.routes

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerIO
import com.rohengiralt.minecraftservermanager.domain.service.WebsocketAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.ConsoleMessageAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerCurrentRunAPIModel
import com.rohengiralt.minecraftservermanager.util.routes.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseUUIDOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseUUIDOrNull
import io.ktor.server.plugins.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Route.websockets() {
    val websocketAPIService: WebsocketAPIService by this@websockets.inject()
    val json: Json by this@websockets.inject()

    route("runners/{runnerId}/runs/current") {
        webSocket {
            call.application.environment.log.info("Received current runs updates websocket connection request for server ${call.parameters["serverId"]}")
            coroutineScope {
                val serverUUID = call.parameters["serverId"]?.parseUUIDOrNull()
                    ?: return@coroutineScope close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Could not parse server UUID"))

                val currentRunsFlow = websocketAPIService.getAllCurrentRunsFlow(serverUUID)
                    ?: return@coroutineScope close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Could not find server with UUID $serverUUID"))

                call.application.environment.log.debug(
                    "Opening current runs updates websocket for server {}",
                    serverUUID
                )

                withContext(Dispatchers.IO) {
                    call.application.environment.log.trace("Collecting current runs updates for server {}", serverUUID)

                    currentRunsFlow.collect { runs: List<MinecraftServerCurrentRun> ->
                        val runAPIModels = runs.map(::MinecraftServerCurrentRunAPIModel)
                        val serializedRuns = json.encodeToString(runAPIModels)
                        outgoing.send(Frame.Text(serializedRuns))
                    }

                    call.application.environment.log.trace(
                        "Finished collecting current runs updates for server {}",
                        serverUUID
                    )
                }

                call.application.environment.log.debug(
                    "Closing current runs updates websocket for server {}",
                    serverUUID
                )
            }
        }

        route("{runId}") {
            webSocket("/console") {
                call.application.environment.log.info("Received console websocket connection request for runner ${call.parameters["runnerId"]}, run ${call.parameters["runId"]}")

                coroutineScope {
                    val runnerUUID = call
                        .getParameterOrBadRequest("runnerId")
                        .parseUUIDOrBadRequest() // TODO: Is BadRequest valid in websocket?
                    val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

                    val runChannel = websocketAPIService.getRunConsoleChannel(runnerId = runnerUUID, runUUID = runUUID)
                        ?: throw NotFoundException()

                    call.application.environment.log.debug(
                        "Opening console websocket for runner {}, run {}",
                        runnerUUID,
                        runUUID
                    )

                    launch(Dispatchers.IO) {
                        call.application.environment.log.trace("Starting console websocket output job for runner {}, run {}", runnerUUID, runUUID)
                        runChannel.consumeAsFlow().collect { message ->
                            call.application.environment.log.trace("Sending {}", message.text)
                            sendSerialized<ConsoleMessageAPIModel>(  // Type parameter is necessary for the "type" field
                                                                     // to be included in the serialized object
                                ConsoleMessageAPIModel.fromServerIO(message)
                            )
                        }

                        close(CloseReason(CloseReason.Codes.NORMAL, "Server stopped")) // If we've reached here, the channel closed
                                                                                                // (i.e., the process has ended)
                        call.application.environment.log.trace("Console websocket output job ended for runner {}, run {}", runnerUUID, runUUID)
                    }

                    launch(Dispatchers.IO) {
                        call.application.environment.log.trace("Starting console websocket input job for runner {}, run {}", runnerUUID, runUUID)
                        incoming.consumeEach {
                            launch {
                                (it as? Frame.Text)?.let { frame ->
                                    call.application.environment.log.trace("Received {}", frame.readText())
                                    runChannel.send(ServerIO.Input.InputMessage(frame.readText()))
                                } ?: call.application.environment.log.warn("Received non-text frame with type {}", it.frameType)
                            }
                        }
                        call.application.environment.log.trace("Console websocket input job ended for runner {}, run {}", runnerUUID, runUUID)
                    }

                    call.application.environment.log.debug(
                        "Closing console websocket for runner {}, run {}",
                        runnerUUID,
                        runUUID
                    )
                }
            }
        }
    }

    route("servers/{serverId}") {
        webSocket {
            call.application.environment.log.info("Received server updates websocket connection request for server {}", call.parameters["serverId"])
            coroutineScope {
                val serverUUID = call.getParameterOrBadRequest("serverId").parseUUIDOrBadRequest()
                val serverUpdatesFlow = websocketAPIService.getServerUpdatesFlow(serverUUID)

                call.application.environment.log.debug("Opening server updates websocket for server {}", serverUUID)

                launch(Dispatchers.IO) {
                    serverUpdatesFlow.collect { server ->
                        val serverAPIModel = server?.let(::MinecraftServerAPIModel)
                        val serializedServer = json.encodeToString(serverAPIModel)
                        outgoing.send(Frame.Text(serializedServer))
                    }
                }

                call.application.environment.log.debug("Closing server updates websocket for server {}", serverUUID)
            }
        }
    }
}