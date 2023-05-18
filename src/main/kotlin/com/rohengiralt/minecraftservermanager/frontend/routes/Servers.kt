package com.rohengiralt.minecraftservermanager.frontend.routes

import com.rohengiralt.minecraftservermanager.domain.service.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.MinecraftServerAPIModel
import com.rohengiralt.minecraftservermanager.plugins.ConflictException
import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
import com.rohengiralt.minecraftservermanager.util.cannotUpdateField
import com.rohengiralt.minecraftservermanager.util.getParameterOrBadRequest
import com.rohengiralt.minecraftservermanager.util.missingField
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.serversRoute() { // TODO: Better response codes in general
    val restApiService: RestAPIService by this@serversRoute.inject()

    get {
        println("Getting all servers")
        call.respond(restApiService.getAllServers().map(::MinecraftServerAPIModel))
    }

    post { serverAPIModel: MinecraftServerAPIModel ->
        println("Adding new server")

        if (serverAPIModel.uuid != null) cannotUpdateField("uuid")

        val name = serverAPIModel.name ?: missingField("name")
        val version = serverAPIModel.version ?: missingField("version")
        val runnerUUID = serverAPIModel.runnerUUID ?: missingField("runnerUUID")

        val success = restApiService.createServer(
            uuid = null, name = name, version = version, runnerUUID = runnerUUID
        )

        if (success) {
            call.respond(HttpStatusCode.Created) // TODO: Respond with the server added (?)
        } else {
            throw ConflictException()
        }
    }

    delete {
        throw NotAllowedException()
    }

    route("/{id}") {
        get {
            println("Getting server with id ${call.parameters["id"]}")
            val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            call.respond(
                restApiService.getServer(uuid = serverUUID)
                    ?.let(::MinecraftServerAPIModel)
                    ?: throw NotFoundException()
            )
        }

        patch {
            println("Patching server with id ${call.parameters["id"]}")
            val serverUUID = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            val serverAPIModel: MinecraftServerAPIModel =
                call.receiveSerializable() // TODO: Can be replaced with body() call?

            if (serverAPIModel.uuid != null) cannotUpdateField("uuid")
            if (serverAPIModel.version != null) cannotUpdateField("version")
            if (serverAPIModel.runnerUUID != null) cannotUpdateField("runnerUUID")

            val success = restApiService.updateServer(
                uuid = serverUUID,
                name = serverAPIModel.name,
            )

            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        put {
            println("Putting server with id ${call.parameters["id"]}")
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
            println("Deleting server with id ${call.parameters["id"]}")
            val uuid = call.getParameterOrBadRequest("id").parseUUIDOrBadRequest()

            val success = restApiService.deleteServer(uuid)

            if (success) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound) // TODO: could also be 500 if server failed to delete
            }
        }
    }
}