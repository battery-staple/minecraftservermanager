package com.rohengiralt.monitor.routing

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * The route at `/status`, which always responds successful if a connection is made.
 * Useful for checking if this application is running.
 */
fun Route.status() {
    get("/status") {
        call.respondText("Running")
    }
}