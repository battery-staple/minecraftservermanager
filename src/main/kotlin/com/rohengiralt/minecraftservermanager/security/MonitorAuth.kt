package com.rohengiralt.minecraftservermanager.security

import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadWriteMutexGuardedResource
import io.ktor.server.auth.*
import java.util.*

// TODO: REMOVE DUMMY TOKEN
val monitorTokens = ReadWriteMutexGuardedResource(listOf<Pair<String, MonitorPrincipal>>("asdf" to MonitorPrincipal(UUID.randomUUID())))

fun AuthenticationConfig.monitorAuth(name: String) {
    bearer(name) {
        authenticate { credential ->
            val validTokens = monitorTokens.get()

            return@authenticate validTokens
                .find { (token, _) -> token == credential.token }
                ?.let { (_, principal) -> principal }
        }
    }
}

data class MonitorPrincipal(val monitorUUID: UUID) : Principal