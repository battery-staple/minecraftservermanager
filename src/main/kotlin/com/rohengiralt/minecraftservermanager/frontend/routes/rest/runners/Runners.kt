package com.rohengiralt.minecraftservermanager.frontend.routes.rest.runners

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerRunnerAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.orThrow
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.routes.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.routes.parseRunnerUUIDOrBadRequest
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.runnersRoute() {
    val restApiService: RestAPIService by this@runnersRoute.inject()
    get {
        call.application.environment.log.info("Getting all runners")
        call.respond(restApiService.getAllRunners().orThrow().map(::MinecraftServerRunnerAPIModel))
    }

    delete {
        throw NotAllowedException()
    }

    route("/{runnerId}") {
        get {
            call.application.environment.log.info("Getting runner with id ${call.parameters["runnerId"]}")
            val runnerUUID = call.getParameterOrBadRequest("runnerId").parseRunnerUUIDOrBadRequest()

            call.respond(
                restApiService.getRunner(runnerUUID).orThrow().let(::MinecraftServerRunnerAPIModel)
            )
        }

        delete {
            throw NotAllowedException()
        }

        route("/runs") {
            route("/current") {
                currentRuns()
            }
            route("/past") {
                pastRuns()
            }
        }
    }
}

