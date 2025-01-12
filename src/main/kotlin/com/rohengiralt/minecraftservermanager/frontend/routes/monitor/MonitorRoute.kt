package com.rohengiralt.minecraftservermanager.frontend.routes.monitor

import com.rohengiralt.minecraftservermanager.domain.service.MonitorAPIService
import com.rohengiralt.minecraftservermanager.security.MonitorPrincipal
import com.rohengiralt.shared.ktor.AuthorizationException
import com.rohengiralt.shared.util.uuid.UUIDSerializer
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Route.monitorRoute() {
    val monitorService: MonitorAPIService by inject()
    val json: Json by inject()

    get("ping") {
        call.respond("pong")
    }

    get("jar") {
        call.application.log.debug("Received monitor jar request")
        val principal = call.principal<MonitorPrincipal>() ?: throw AuthorizationException()
        call.application.log.info("Serving jar for {}", principal)

        val jar = monitorService.getJar(principal.serverUUID)

        call.respondFile(jar)
    }

    get("sha1") {
        call.application.log.debug("Received monitor jar sha1 request")
        val principal = call.principal<MonitorPrincipal>() ?: throw AuthorizationException()
        call.application.log.info("Serving sha1 for {}", principal)

        val hash = monitorService.getSHA1(principal.serverUUID)

        call.respondBytes(hash)
    }

    get("run") {
        call.application.log.debug("Received runUUID request")
        val principal = call.principal<MonitorPrincipal>() ?: throw AuthorizationException()
        call.application.log.info("Serving runUUID for {}", principal)

        val uuid = monitorService.getRunUUID(principal.serverUUID) ?: throw NotFoundException()

        call.respond(json.encodeToString(UUIDSerializer, uuid.value))
    }
}

