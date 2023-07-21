package com.rohengiralt.minecraftservermanager.plugins

import GoogleAPIHTTPClients
import com.rohengiralt.minecraftservermanager.auth.UserIdVerifier
import com.rohengiralt.minecraftservermanager.auth.UserSession
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientId
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientSecret
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.cookieSecretEncryptKey
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.cookieSecretSignKey
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    val httpClients: GoogleAPIHTTPClients by inject()
    val userIdVerifier: UserIdVerifier by inject()

    install(Sessions) {
        cookie<UserSession>("user_session", directorySessionStorage(File(".ktor/sessions"))) {
            cookie.path = "/"
            cookie.maxAge = 30.days
            transform(SessionTransportTransformerEncrypt(hex(securityConfig[cookieSecretEncryptKey]), hex(securityConfig[cookieSecretSignKey])))
        }

    }

    val redirects = mutableMapOf<String, String>()
    install(Authentication) {
        basic("auth-debug") {
            validate { credentials ->
                if (credentials.name == "User McUserface" && credentials.password == "Super secure password")
                    UserIdPrincipal(credentials.name)
                else null
            }
        }

        session<UserSession>("auth-session") {
            validate {
                val userInfo: UserInfo = httpClients.getClient(sessions)
                    .get("https://www.googleapis.com/oauth2/v2/userinfo")
                    .body()

                println("Validating userinfo $userInfo")

                if (userIdVerifier.isAuthorized(userInfo.id)) userInfo else null
            }

            challenge {
                val userSession = it ?: call.sessions.get<UserSession>()

                val redirectUrl = URLBuilder("http://localhost:8080/login").run {
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
            urlProvider = { "http://localhost:8080/callback" }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
                    requestMethod = HttpMethod.Post,
                    clientId = securityConfig[clientId],
                    clientSecret = securityConfig[clientSecret],
                    defaultScopes = listOf("https://www.googleapis.com/auth/userinfo.profile"),
                    extraAuthParameters = listOf("access_type" to "offline"),
                    onStateCreated = { call, state ->
                        redirects[state] = call.request.queryParameters["redirectUrl"] ?: "http:localhost:8080/"
                    }
                )
            }
            client = httpClients.getClient()
        }
    }

    routing {
        authenticate("auth-oauth-google") {
            get("/login") {
                // Redirects to 'authorizeUrl' automatically
            }

            get("/callback") {
                val principal: OAuthAccessTokenResponse.OAuth2? = call.principal()
                principal ?: throw BadRequestException("Invalid token response")
                val state = principal.state ?: throw BadRequestException("Missing state")

                call.sessions.set(
                    UserSession(
                        state,
                        principal.accessToken,
                        principal.refreshToken ?: throw BadRequestException("Missing refresh token"),
                    )
                )
                val redirect = redirects[state]
                call.respondRedirect(redirect ?: "http://localhost:8080/")
            }
        }
    }
}

@Serializable
private data class UserInfo(
    val id: String,
    val name: String,
    @SerialName("given_name") val givenName: String,
    @SerialName("family_name") val familyName: String,
    val picture: String,
    val locale: String
) : Principal