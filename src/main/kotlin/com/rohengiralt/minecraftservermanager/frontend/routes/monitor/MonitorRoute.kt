package com.rohengiralt.minecraftservermanager.frontend.routes.monitor

import com.rohengiralt.minecraftservermanager.domain.service.MonitorAPIService
import com.rohengiralt.minecraftservermanager.plugins.AuthorizationException
import com.rohengiralt.minecraftservermanager.security.MonitorPrincipal
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.auth.principal
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Route.monitorRoute() {
    val monitorService: MonitorAPIService by inject()

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
}

