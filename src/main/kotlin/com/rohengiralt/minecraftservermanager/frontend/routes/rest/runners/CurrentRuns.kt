package com.rohengiralt.minecraftservermanager.frontend.routes.rest.runners

import com.rohengiralt.minecraftservermanager.domain.service.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerCurrentRunAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerEnvironmentAPIModel
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.routes.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseUUIDOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.receiveSerializable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.currentRuns() {
    val restApiService: RestAPIService by this@currentRuns.inject()
    get {
        println("Getting all runs")
        val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()

        val runs = restApiService.getAllCurrentRuns(runnerUUID)
            ?.map(::MinecraftServerCurrentRunAPIModel)
            ?: throw NotFoundException()

        call.respond(runs)
    }

    post {
        println("Creating new run")
        val serverUUID = call.getParameterOrBadRequest("serverId").parseUUIDOrBadRequest()
        val environment = call.receiveSerializable<MinecraftServerEnvironmentAPIModel>().toMinecraftServerEnvironment()

        val createdRun = restApiService.createCurrentRun(serverUUID, environment)

        if (createdRun == null) {
            call.respond(HttpStatusCode.InternalServerError)
        } else {
            call.respond(MinecraftServerCurrentRunAPIModel(createdRun))
        }
    }

    delete {
        println("Stopping current runs")

        val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()

        val success = restApiService.stopAllCurrentRuns(runnerUUID)

        if (success) {
            call.respond(HttpStatusCode.OK) // TODO: Respond with past runs
        } else {
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    route("/{runId}") {
        get {
            val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()
            val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

            call.respond(
                restApiService
                    .getCurrentRun(
                        runnerUUID = runnerUUID,
                        runUUID = runUUID
                    )
                    ?.let(::MinecraftServerCurrentRunAPIModel)
                    ?: throw NotFoundException()
            )
        }

        put {
            throw NotAllowedException()
        }

        patch {
            throw NotAllowedException()
        }

        delete {
            println("Ending run")
            val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()
            val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

            val success = restApiService.stopCurrentRun(runUUID = runUUID, runnerUUID = runnerUUID)

            if (success) {
                call.respond(HttpStatusCode.OK) // TODO: Respond with new past run
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        route("/consoleURL") {
            get {
                val runId = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()
                call.respond("/api/v2/websockets/runs/${runId}/console") // TODO: prevent hardcoding URL by using Resources plugin
            }
        }
    }
}