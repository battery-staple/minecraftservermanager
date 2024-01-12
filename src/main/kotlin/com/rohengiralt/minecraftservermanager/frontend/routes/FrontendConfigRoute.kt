package com.rohengiralt.minecraftservermanager.frontend.routes

import com.rohengiralt.minecraftservermanager.hostname
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.frontendConfig() {
    get("/config.json") {
        call.respond(FrontendConfig(hostname))
    }
}

@Serializable
private data class FrontendConfig(
    val hostname: String
)