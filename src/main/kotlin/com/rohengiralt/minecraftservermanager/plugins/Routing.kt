package com.rohengiralt.minecraftservermanager.plugins

import com.rohengiralt.minecraftservermanager.frontend.routes.runners.runnersRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.runsRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.serversRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.websockets
import io.ktor.http.*
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
import org.koin.ktor.ext.get

fun Application.configureRouting() {
    install(WebSockets)
    install(ContentNegotiation) {
        json(this@configureRouting.get())
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        anyHost() //TODO: only for testing
    }

    install(Authentication) {
        basic {
            validate { credentials ->
                if (credentials.name == "User McUserface" && credentials.password == "Super secure password")
                    UserIdPrincipal(credentials.name)
                else null
            }
        }
    }

    install(StatusPages) {
        exception<AuthenticationException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized)
        }
        exception<AuthorizationException> { call, _ ->
            call.respond(HttpStatusCode.Forbidden)
        }
        exception<BadRequestException> { call, exception ->
            call.respond(HttpStatusCode.BadRequest, exception.message ?: "Bad request")
        }
        exception<ConflictException> { call, _ ->
            call.respond(HttpStatusCode.Conflict)
        }
        exception<NotAllowedException> { call, _ ->
            call.respond(HttpStatusCode.MethodNotAllowed)
        }
        exception<NotImplementedError> { call, _ ->
            call.respond(HttpStatusCode.NotImplemented)
        }
    }

    routing {
        get("/") {
            call.respondRedirect("/index.html")
        }

        route("api/v2") {
            authenticate {
                route("/rest") {
                    route("/servers") {
                        serversRoute()
                    }
                    route("/runners") {
                        runnersRoute()
                    }
                    route("/runs") {
                        runsRoute()
                    }
                }
            }
            route("/websockets") {
                websockets()
            }
        }

        static("/") {
            resources("static/react")
        }
    }
}

class AuthenticationException : RuntimeException()
class AuthorizationException : RuntimeException()
class ConflictException : RuntimeException()
class NotAllowedException : RuntimeException()