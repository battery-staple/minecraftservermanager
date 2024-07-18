package com.rohengiralt.minecraftservermanager.frontend.routes.rest.runners

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerPastRunAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.orThrow
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.routes.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseRunUUIDOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseServerUUIDOrBadRequest
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.pastRuns() {
    val restApiService: RestAPIService by this@pastRuns.inject()

    get {
        call.application.environment.log.info("Getting all past runs")
        val serverUUID = call.getParameterOrBadRequest("serverId").parseServerUUIDOrBadRequest()

        call.respond(restApiService.getAllPastRuns(serverUUID).orThrow().map(::MinecraftServerPastRunAPIModel))
    }

    route("/{runId}") {
        get {
            val runUUID = call.getParameterOrBadRequest("runId").parseRunUUIDOrBadRequest()

            call.respond(
                restApiService
                    .getPastRun(runUUID = runUUID).orThrow()
                    .let(::MinecraftServerPastRunAPIModel)
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