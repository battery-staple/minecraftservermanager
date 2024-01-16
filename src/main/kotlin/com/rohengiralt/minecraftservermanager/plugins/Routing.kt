package com.rohengiralt.minecraftservermanager.plugins

import com.rohengiralt.minecraftservermanager.frontend.routes.frontendConfig
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.runners.runnersRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.serversRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.statusRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.usersRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.websockets
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.get

fun Application.configureRouting() {
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(this@configureRouting.get<Json>())
    }

    install(ContentNegotiation) {
        json(this@configureRouting.get<Json>())
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        anyHost() //TODO: only for testing
    }

    install(StatusPages) {
        exception<Throwable> { call, e ->
            when (e) {
                is AuthenticationException -> call.respond(HttpStatusCode.Unauthorized, e.message ?: "Unauthorized")
                is AuthorizationException -> call.respond(HttpStatusCode.Forbidden, e.message ?: "Forbidden")
                is BadRequestException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "Bad request")
                is NotFoundException -> call.respond(HttpStatusCode.NotFound, e.message ?: "Not found")
                is ConflictException -> call.respond(HttpStatusCode.Conflict, e.message ?: "Conflict")
                is NotAllowedException -> call.respond(HttpStatusCode.MethodNotAllowed, e.message ?: "Not allowed")
                is NotImplementedError -> call.respond(HttpStatusCode.NotImplemented, e.message ?: "Not implemented")
                is InternalServerException -> call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal Server Error")
                else -> call.application.environment.log.error("Uncaught exception:\n${e.stackTraceToString()}")
            }
        }
    }

    routing {
        authenticate("auth-session", "auth-debug") {
            route("api/v2") {
                route("/rest") {
                    route("/servers") {
                        serversRoute()
                    }
                    route("/runners") {
                        runnersRoute()
                    }
                    route("/status") {
                        statusRoute()
                    }
                    route("/users") {
                        usersRoute()
                    }
                }
            }

            singlePageApplication {
                useResources = true
                react("static/react")
            }

            route("/config") {
                frontendConfig()
            }
        }

        authenticate("auth-debug", "auth-session") {
            route("api/v2/websockets") {
                websockets()
            }
        }
    }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
class ConflictException : RuntimeException()
class NotAllowedException : RuntimeException()
class InternalServerException : RuntimeException()