package com.rohengiralt.minecraftservermanager.plugins

import com.rohengiralt.minecraftservermanager.frontend.routes.frontendConfig
import com.rohengiralt.minecraftservermanager.frontend.routes.monitor.monitorRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.runners.runnersRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.serversRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.statusRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.rest.usersRoute
import com.rohengiralt.minecraftservermanager.frontend.routes.websockets
import com.rohengiralt.shared.ktor.configureStatusPagesExceptionHandling
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
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
        allowHeader(HttpHeaders.ContentType)
        anyHost() //TODO: only for testing
    }

    install(StatusPages) {
        configureStatusPagesExceptionHandling()
    }

    routing {
        authenticate(*httpAuthProviders) {
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

        authenticate(*websocketAuthProviders) {
            route("api/v2/websockets") {
                websockets()
            }
        }

        authenticate(*monitorAuthProviders) {
            route("api/monitor/v1") {
                monitorRoute()
            }
        }
    }
}