package com.rohengiralt.minecraftservermanager.plugins

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.rohengiralt.minecraftservermanager.debugMode
import com.rohengiralt.minecraftservermanager.hostname
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientId
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientSecret
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.cookieSecretEncryptKey
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.cookieSecretSignKey
import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.user.auth.UserSession
import com.rohengiralt.minecraftservermanager.user.auth.google.UserIDAuthorizer
import com.rohengiralt.minecraftservermanager.user.auth.google.idTokenVerifier
import com.rohengiralt.minecraftservermanager.user.auth.google.verifyUserSessionIdToken
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.notEmptyOr
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.html.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.p
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import kotlin.collections.set
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

@Suppress("InjectedReferences")
fun Application.configureSecurity() {
    val authorizer: UserIDAuthorizer by inject()

    install(Sessions) {
        cookie<UserSession>("user_session", directorySessionStorage(File("/minecraftservermanager/.ktor/sessions"))) {
            cookie.path = "/"
            cookie.maxAge = 30.days
            transform(SessionTransportTransformerEncrypt(hex(securityConfig[cookieSecretEncryptKey]), hex(securityConfig[cookieSecretSignKey])))
        }
    }

    val redirects = mutableMapOf<String, String>()
    install(Authentication) {
        if (debugMode) {
            val validateDebugCredentials = { _: ApplicationCall, credentials: UserPasswordCredential ->
                if (credentials.name.startsWith("User McUserface") && credentials.password == "Super secure password") {
                    logger.info("Authenticating debug user with credentials $credentials") // TODO: definitely remove the logging of credentials
                    UserLoginInfo(
                        userId = UserID(credentials.name.removePrefix("User McUserface").notEmptyOr("1")),
                        email = "user@example.com"
                    )
                } else null
            }

            basic("auth-debug") {
                validate(validateDebugCredentials)
            }

            register(DebugURLParamAuthenticationProvider(
                DebugURLParamAuthenticationProvider.Config("auth-debug-websocket", validateDebugCredentials)
            ))
        }

        session<UserSession>("auth-session") {
            validate { session ->
                logger.debug("Preparing to validate userinfo")
                val idToken: GoogleIdToken = idTokenVerifier.verifyUserSessionIdToken(
                    userSession = session,
                    sessions = sessions
                ) ?: return@validate null

                val userLoginInfo = UserLoginInfo(
                    userId = UserID(idToken.payload.subject ?: return@validate null),
                    email = idToken.payload.email ?: return@validate null
                )

                return@validate if (authorizer.isAuthorized(userLoginInfo.userId)) userLoginInfo else null
            }

            challenge { userSession ->
                logger.debug("Received session authentication challenge")

                val redirectUrl = URLBuilder("http://$hostname/login").run {
                    parameters.append("redirectUrl", call.request.uri)
                    build()
                }

                if (userSession == null) {
                    call.respondRedirect(redirectUrl)
                } else {
                    call.respondHtml {
                        body {
                            p {
                                +"The logged in user is not authorized."
                                br()
                                a(redirectUrl.toString()) { +"Try logging in again" }
                            }
                        }
                    }
                }
            }
        }

        oauth("auth-oauth-google") {
            urlProvider = { "http://$hostname/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = securityConfig[clientId],
                    clientSecret = securityConfig[clientSecret],
                    defaultScopes = listOf("openid", "profile", "email", "https://www.googleapis.com/auth/userinfo.profile"),
                    extraAuthParameters = listOf("access_type" to "offline"),
                    onStateCreated = { call, state ->
                        redirects[state] = call.request.queryParameters["redirectUrl"] ?: "http:$hostname/"
                    }
                )
            }
            client = oauthClient
        }
    }

    routing {
        authenticate("auth-oauth-google") {
            get("/login") {
                // Redirects to 'authorizeUrl' automatically
            }

            get("/callback") {
                logger.debug("Auth callback received")
                val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                principal ?: throw BadRequestException("Invalid token response")
                val state = principal.state ?: throw BadRequestException("Missing state")

                call.sessions.set(
                    UserSession(
                        refreshToken = principal.refreshToken ?: throw BadRequestException("Missing refresh token"),
                        idToken = principal.extraParameters["id_token"] ?: throw BadRequestException("Missing id token")
                    )
                )
                val redirect = redirects[state] ?: throw BadRequestException("Unknown state")
                call.respondRedirect(redirect)
            }
        }
    }
}

private val oauthClient = HttpClient(OkHttp) {
    engine {
        config {
            followRedirects(true)
        }
    }
    install (ContentNegotiation) {
        json()
    }
}

val httpAuthProviders = if (debugMode) arrayOf("auth-session", "auth-debug") else arrayOf("auth-session")
val websocketAuthProviders = if (debugMode) arrayOf("auth-debug-websocket", "auth-debug", "auth-session") else arrayOf("auth-session")

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

private val logger = LoggerFactory.getLogger("Security")
