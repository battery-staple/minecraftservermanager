package com.rohengiralt.minecraftservermanager.frontend.routes.runners

import com.rohengiralt.minecraftservermanager.domain.service.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerPastRunAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.parseUUIDOrBadRequest
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.getParameterOrBadRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.pastRuns() {
    val restApiService: RestAPIService by this@pastRuns.inject()

    get {
        println("Getting all past runs")
        val serverUUID = call.getParameterOrBadRequest("serverId").parseUUIDOrBadRequest()

        call.respond(restApiService.getAllPastRuns(serverUUID).map(::MinecraftServerPastRunAPIModel))
    }

    route("/{runId}") {
        get {
            val serverUUID = call.getParameterOrBadRequest("serverId").parseUUIDOrBadRequest()
            val runUUID = call.getParameterOrBadRequest("runId").parseUUIDOrBadRequest()

            call.respond(
                restApiService
                    .getPastRun(serverUUID = serverUUID, runUUID = runUUID)
                    ?.let(::MinecraftServerPastRunAPIModel)
                    ?: throw NotFoundException()
            )
        }

        post {
            throw NotAllowedException()
        }

        put {
            throw NotAllowedException()
        }

        patch {
            throw NotAllowedException()
        }

        delete {
            throw NotAllowedException()
        }
    }
}