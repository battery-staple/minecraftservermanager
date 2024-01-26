package com.rohengiralt.minecraftservermanager.frontend.routes.rest

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.statusRoute() {
    get {
        call.respond("Running!")
    }
}