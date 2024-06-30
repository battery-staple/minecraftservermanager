package com.rohengiralt.minecraftservermanager.frontend.routes.rest

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerCurrentRunAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerEnvironmentAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.orThrow
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.routes.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.serversRoute() { // TODO: Better response codes in general
    val restApiService: RestAPIService by this@serversRoute.inject()

    get {
        call.application.environment.log.info("Getting all servers")
        call.respond(restApiService.getAllServers().orThrow().map(::MinecraftServerAPIModel))
    }

    post {
        call.application.environment.log.info("Adding new server")

        val serverAPIModel: MinecraftServerAPIModel = call.receiveSerializable()

        if (serverAPIModel.uuid != null) cannotUpdateField("uuid")
        if (serverAPIModel.creationTime != null) cannotUpdateField("creationTime")

        val name = serverAPIModel.name ?: missingField("name")
        val version = serverAPIModel.version ?: missingField("version")
        val runnerUUID = serverAPIModel.runnerUUID ?: missingField("runnerUUID")

        val newServer = restApiService.createServer(
            uuid = null, name = name, version = version, runnerUUID = runnerUUID
        ).orThrow()

        call.respond(HttpStatusCode.Created, newServer.let(::MinecraftServerAPIModel))
    }

    delete {
        throw NotAllowedException()
    }

    route("/{id}") {
        get {
            call.application.environment.log.info("Getting server with id ${call.parameters["id"]}")
            val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            call.respond(
                restApiService.getServer(uuid = serverUUID).orThrow().let(::MinecraftServerAPIModel)
            )
        }

        patch {
            call.application.environment.log.info("Patching server with id ${call.parameters["id"]}")
            val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            val serverAPIModel: MinecraftServerAPIModel = call.receiveSerializable()

            if (serverAPIModel.uuid != null) cannotUpdateField("uuid")
            if (serverAPIModel.version != null) cannotUpdateField("version")
            if (serverAPIModel.runnerUUID != null) cannotUpdateField("runnerUUID")

            restApiService.updateServer(
                uuid = serverUUID,
                name = serverAPIModel.name,
            ).orThrow()
        }

        put {
            call.application.environment.log.info("Putting server with id ${call.parameters["id"]}")
            val uuid = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            val serverAPIModel: MinecraftServerAPIModel = call.receiveSerializable()

            if (serverAPIModel.uuid != null) cannotUpdateField("uuid")
            val name = serverAPIModel.name ?: missingField("name")
            val version = serverAPIModel.version ?: missingField("version")
            val runnerUUID = serverAPIModel.runnerUUID ?: missingField("runnerUUID")

            val success = restApiService.setServer(
                uuid = uuid,
                name = name,
                version = version,
                runnerUUID = runnerUUID,
            )

            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        delete {
            call.application.environment.log.info("Deleting server with id ${call.parameters["id"]}")
            val uuid = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            val success = restApiService.deleteServer(uuid)

            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound) // TODO: could also be 500 if server failed to delete
            }
        }

        route("/currentRun") {
            get {
                call.application.environment.log.info("Getting current run for server with id ${call.parameters["id"]}")
                val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

                val run = restApiService
                    .getCurrentRunByServer(serverUUID)
                    ?.let(::MinecraftServerCurrentRunAPIModel)
                    ?: throw NotFoundException()

                call.respond(run)
            }

            post {
                call.application.environment.log.info("Creating new run for server with id ${call.parameters["id"]}")
                val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()
                val environment = call.receiveSerializable<MinecraftServerEnvironmentAPIModel>().toMinecraftServerEnvironment()

                val createdRun = restApiService.createCurrentRun(serverUUID, environment)

                if (createdRun == null) {
                    call.respond(HttpStatusCode.InternalServerError)
                } else {
                    call.respond(MinecraftServerCurrentRunAPIModel(createdRun))
                }
            }

            delete {
                call.application.environment.log.info("Stopping current run for server with id ${call.parameters["id"]}")

                val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

                val success = restApiService.stopCurrentRunByServer(serverUUID)

                if (success) {
                    call.respond(HttpStatusCode.OK) // TODO: Respond with past run
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}