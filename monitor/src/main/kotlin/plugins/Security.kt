package com.rohengiralt.monitor.plugins

import com.rohengiralt.monitor.token
import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureSecurity() {
    install(Authentication) {
        bearer {
            authenticate { credential ->
                if (credential.token == token) Authenticated else null
            }
        }
    }
}

private data object Authenticated : Principal