package com.rohengiralt.minecraftservermanager.plugins

import com.rohengiralt.minecraftservermanager.debugMode
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.cookieSecretEncryptKey
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.cookieSecretSignKey
import com.rohengiralt.minecraftservermanager.security.debugAuth
import com.rohengiralt.minecraftservermanager.security.googleSessionAuth
import com.rohengiralt.minecraftservermanager.security.googleSessionAuthRoute
import com.rohengiralt.minecraftservermanager.security.monitorAuth
import com.rohengiralt.minecraftservermanager.user.auth.UserSession
import com.rohengiralt.minecraftservermanager.user.auth.google.UserIDAuthorizer
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import org.koin.ktor.ext.inject
import java.io.File
import kotlin.time.Duration.Companion.days


internal val securityConfig = Config { addSpec(SecuritySpec) }
    .from.env()

internal object SecuritySpec : ConfigSpec() {
    val clientId by required<String>()
    val clientSecret by required<String>()
    val cookieSecretEncryptKey by required<String>()
    val cookieSecretSignKey by required<String>()
    val whitelistFile by optional<String>("/minecraftservermanager/whitelist.txt")
}

fun Application.configureSecurity() {
    val authorizer: UserIDAuthorizer by inject()

    install(Sessions) {
        cookie<UserSession>("user_session", directorySessionStorage(File("/minecraftservermanager/.ktor/sessions"))) {
            cookie.path = "/"
            cookie.maxAge = 30.days
            transform(
                SessionTransportTransformerEncrypt(
                    hex(securityConfig[cookieSecretEncryptKey]),
                    hex(securityConfig[cookieSecretSignKey])
                )
            )
        }
    }

    install(Authentication) {
        if (debugMode) {
            debugAuth(debugAuthName, websocketsDebugAuthName)
        }

        googleSessionAuth(sessionAuthName, authorizer)
        monitorAuth(monitorAuthName)
    }

    googleSessionAuthRoute()
}

private const val sessionAuthName = "auth-session"
private const val debugAuthName = "auth-debug"
private const val websocketsDebugAuthName = "auth-debug-websocket"
private const val monitorAuthName = "auth-monitor"

val httpAuthProviders = if (debugMode) arrayOf(sessionAuthName, debugAuthName) else arrayOf(sessionAuthName)
val websocketAuthProviders =
    if (debugMode) arrayOf(websocketsDebugAuthName, debugAuthName, sessionAuthName) else arrayOf(sessionAuthName)
val monitorAuthProviders = arrayOf(monitorAuthName)