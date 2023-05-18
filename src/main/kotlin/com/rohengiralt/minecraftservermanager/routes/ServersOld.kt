//package com.rohengiralt.minecraftservermanager.routes
//
//import com.rohengiralt.minecraftservermanager.displaySerializationStrategy.MinecraftServerDisplay
//import com.rohengiralt.minecraftservermanager.model.Port
//import com.rohengiralt.minecraftservermanager.plugins.BadRequestException
//import com.rohengiralt.minecraftservermanager.plugins.ConflictException
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerFactory
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerRepository
//import io.ktor.application.*
//import io.ktor.features.*
//import io.ktor.http.*
//import io.ktor.request.*
//import io.ktor.response.*
//import io.ktor.routing.*
//import kotlinx.serialization.SerializationException
//import org.koin.ktor.ext.inject
//import java.util.*
//
//fun Route.serversOldRoute() {
//    val serverRepo: MinecraftServerRepository by this@serversOldRoute.inject()
//    val serverFactory: MinecraftServerFactory by this@serversOldRoute.inject()
//
//    get {
//        println("Getting all servers")
//        call.respond(serverRepo.getAllServers().map(::MinecraftServerDisplay))
//    }
//
//    post {
//        var serverDisplay = try {
//            call.receive<MinecraftServerDisplay>()
//        } catch (e: SerializationException) {
//            throw BadRequestException()
//        }
//
//        serverDisplay = serverDisplay.run {
//            copy(
//                id = id ?: UUID.randomUUID(),
//                port = port ?: Port.defaultMinecraftServerPort
//            )
//        }
//
//        with(serverFactory) {
//            serverDisplay
//                .createServer()
//                ?.also {
//                    if (!serverRepo.saveServer(it)) throw ConflictException()
//                } ?: throw BadRequestException()
//        }
//
//        call.respond(HttpStatusCode.Created, serverDisplay)
//    }
//
//    route("/{id}") {
//        get {
//            val server = serverRepo.getServer(UUID.fromString(call.parameters["id"] ?: throw BadRequestException())) ?: throw NotFoundException()
//            call.respond(MinecraftServerDisplay(server))
//        }
//
//        put {
//            val serverDisplay = try {
//                call.receive<MinecraftServerDisplay>()
//            } catch (e: SerializationException) {
//                throw BadRequestException()
//            }
//
//            val server = with(serverFactory) {
//                serverDisplay
//                    .createServer()
//                    ?.also {
//                        serverRepo.saveServer(it)
//                    } ?: throw BadRequestException()
//            }
//            call.respond(server)
//        }
//
//        patch {
//            val server = serverRepo.getServer(UUID.fromString(call.parameters["id"] ?: throw BadRequestException())) ?: throw NotFoundException()
//            val serverDisplay = try {
//                call.receive<MinecraftServerDisplay>()
//            } catch (e: SerializationException) {
//                throw BadRequestException()
//            }
//
//            if (serverDisplay.id != null)
//                throw BadRequestException()
//
//            server.run {
//                name = serverDisplay.name ?: name
//                version = serverDisplay.version ?: version
//                port = serverDisplay.port ?: port
//            }
//
//            call.respond(MinecraftServerDisplay(server))
//        }
//
//        delete {
//            val uuid = UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//            val success = serverRepo.removeServer(uuid)
//
//            if (success) call.respond(HttpStatusCode.NoContent) else throw NotFoundException()
//        }
//
////        runsRoutes()
//    }
//}
//
