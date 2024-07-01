package com.rohengiralt.minecraftservermanager.frontend.routes.rest.runners

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerCurrentRunAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerEnvironmentAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.orThrow
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.routes.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseUUIDOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.receiveSerializable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.currentRuns() {
    val restApiService: RestAPIService by this@currentRuns.inject()
    get {
        call.application.environment.log.info("Getting all runs")
        val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()

        val runs = restApiService
            .getAllCurrentRuns(runnerUUID).orThrow()
            .map(::MinecraftServerCurrentRunAPIModel)

        call.respond(runs)
    }

    post {
        call.application.environment.log.info("Creating new run")
        val serverUUID = call.getParameterOrBadRequest("serverId").parseUUIDOrBadRequest()
        val environment = call.receiveSerializable<MinecraftServerEnvironmentAPIModel>().toMinecraftServerEnvironment()

        val createdRun = restApiService.createCurrentRun(serverUUID, environment).orThrow()
        call.respond(MinecraftServerCurrentRunAPIModel(createdRun))
    }

    delete {
        call.application.environment.log.info("Stopping current runs")

        val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()

        restApiService.stopAllCurrentRuns(runnerUUID).orThrow()

        call.respond(HttpStatusCode.OK) // TODO: Respond with past runs
    }

    route("/{runId}") {
        get {
            val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()
            val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

            call.respond(
                restApiService
                    .getCurrentRun(runnerUUID = runnerUUID, runUUID = runUUID).orThrow()
                    .let(::MinecraftServerCurrentRunAPIModel)
            )
        }

        put {
            throw NotAllowedException()
        }

        patch {
            throw NotAllowedException()
        }

        delete {
            call.application.environment.log.info("Ending run")
            val runnerUUID = call.getParameterOrBadRequest("runnerId").parseUUIDOrBadRequest()
            val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

            restApiService.stopCurrentRun(runUUID = runUUID, runnerUUID = runnerUUID).orThrow()

            call.respond(HttpStatusCode.OK) // TODO: Respond with new past run
        }

        route("/consoleURL") {
            get {
                val runId = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()
                call.respond("/api/v2/websockets/runs/${runId}/console") // TODO: prevent hardcoding URL by using Resources plugin
            }
        }
    }
}