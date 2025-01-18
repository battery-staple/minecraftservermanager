package com.rohengiralt.minecraftservermanager.security

import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.MonitorToken
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.repository.MonitorTokenRepository
import io.ktor.server.auth.*
import org.koin.core.context.GlobalContext

fun AuthenticationConfig.monitorAuth(name: String) {
    bearer(name) {
        authenticate { credential ->
            val serverUUID = monitorTokens.getServerWithToken(MonitorToken.fromString(credential.token)) ?: return@authenticate null

            return@authenticate MonitorPrincipal(serverUUID)
        }
    }
}

/**
 * A [Principal] authenticating a particular monitor instance
 * @param serverUUID the uuid of the server the monitor belongs to
 */
data class MonitorPrincipal(val serverUUID: ServerUUID) : Principal

private val monitorTokens: MonitorTokenRepository by GlobalContext.get().inject()