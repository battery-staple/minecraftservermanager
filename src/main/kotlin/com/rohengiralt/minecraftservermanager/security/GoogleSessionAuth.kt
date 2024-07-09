package com.rohengiralt.minecraftservermanager.security

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.rohengiralt.minecraftservermanager.hostname
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientId
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientSecret
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.user.auth.UserSession
import com.rohengiralt.minecraftservermanager.user.auth.google.UserIDAuthorizer
import com.rohengiralt.minecraftservermanager.user.auth.google.idTokenVerifier
import com.rohengiralt.minecraftservermanager.user.auth.google.verifyUserSessionIdToken
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
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.p
import org.slf4j.LoggerFactory
import kotlin.collections.set

private val redirects = mutableMapOf<String, String>()

/**
 * Configures session-based authentication backed by an OAuth Google Accounts login.
 * @param name the name of the auth provider
 * @param authorizer the authorizer used to validate principals
 */
fun AuthenticationConfig.googleSessionAuth(
    name: String,
    authorizer: UserIDAuthorizer,
) {
    session<UserSession>(name) {
        validate { session ->
            logger.debug("Preparing to validate user session")
            val idToken: GoogleIdToken = idTokenVerifier.verifyUserSessionIdToken(
                userSession = session,
                sessions = sessions
            ) ?: return@validate null

            logger.debug("Validating session for user '${idToken.payload.subject}' (email='${idToken.payload.email}')")

            val userLoginInfo = UserLoginInfo(
                userId = UserID(idToken.payload.subject ?: return@validate null),
                email = idToken.payload.email ?: return@validate null
            )

            return@validate if (authorizer.isAuthorized(userLoginInfo.userId)) userLoginInfo else null // TODO: do authorization separately
        }

        challenge { userSession ->
            logger.debug("Making session authentication challenge")

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
                defaultScopes = listOf(
                    "openid",
                    "profile",
                    "email",
                    "https://www.googleapis.com/auth/userinfo.profile"
                ),
                extraAuthParameters = listOf("access_type" to "offline"),
                onStateCreated = { call, state ->
                    redirects[state] = call.request.queryParameters["redirectUrl"] ?: "http:$hostname/"
                }
            )
        }
        client = oauthClient
    }
}

/**
 * Configures routes required for Google OAuth.
 * Requires that [googleSessionAuth] has already been configured.
 */
fun Application.googleSessionAuthRoute() {
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

                if (principal.refreshToken == null) logger.warn("Missing refresh token")

                val newSession = UserSession(
                    refreshToken = principal.refreshToken,
                    idToken = principal.extraParameters["id_token"] ?: throw BadRequestException("Missing id token")
                )

                call.sessions.set(newSession)
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

private val logger = LoggerFactory.getLogger("GoogleSessionAuth")