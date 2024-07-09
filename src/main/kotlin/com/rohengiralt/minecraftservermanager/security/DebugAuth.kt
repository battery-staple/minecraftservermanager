package com.rohengiralt.minecraftservermanager.security

import com.rohengiralt.minecraftservermanager.debugMode
import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.uchuhimo.konf.notEmptyOr
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Installs the necessary authentication providers for debug authentication.
 * Precondition: must only be called in debug mode.
 * @param httpName the name of the auth provider for HTTP routes
 * @param websocketsName the name of the auth provider for websocket routes
 */
fun AuthenticationConfig.debugAuth(httpName: String, websocketsName: String) {
    assert(debugMode) { "Debug auth is not permitted in production." }

    val validateDebugCredentials = { _: ApplicationCall, credentials: UserPasswordCredential ->
        if (credentials.name.startsWith("User McUserface") && credentials.password == "Super secure password") {
            logger.info("Authenticating debug user with credentials $credentials") // TODO: definitely remove the logging of credentials
            UserLoginInfo(
                userId = UserID(credentials.name.removePrefix("User McUserface").notEmptyOr("1")),
                email = "user@example.com"
            )
        } else null
    }

    basic(httpName) {
        validate(validateDebugCredentials)
    }

    register(
        DebugURLParamAuthenticationProvider(
            DebugURLParamAuthenticationProvider.Config(websocketsName, validateDebugCredentials)
        )
    )
}

/**
 * An [AuthenticationProvider] that authenticates using the `name` and `password` url parameters.
 * Useful for authenticating Websockets in debug mode, as the JavaScript `fetch` API does not support
 * the `Authorization` header.
 * Not intended for use outside of debug mode.
 */
private class DebugURLParamAuthenticationProvider(private val config: Config) : AuthenticationProvider(config) {
    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val usernameBase64 = context.call.request.queryParameters["name"] ?: return
        val passwordBase64 = context.call.request.queryParameters["password"] ?: return

        val username = usernameBase64.let { Base64.getUrlDecoder().decode(it) }.toString(Charsets.ISO_8859_1)
        val password = passwordBase64.let { Base64.getUrlDecoder().decode(it) }.toString(Charsets.ISO_8859_1)

        val credential = UserPasswordCredential(name = username, password = password)

        config.validate(context.call, credential)?.let(context::principal)
    }

    class Config(name: String, val validate: ApplicationCall.(UserPasswordCredential) -> Principal?) : AuthenticationProvider.Config(name)
}

private val logger = LoggerFactory.getLogger("debugAuth")