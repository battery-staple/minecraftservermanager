package com.rohengiralt.minecraftservermanager.user.auth.google

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import com.rohengiralt.minecraftservermanager.user.auth.UserSession
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.getKoin

val idTokenVerifier: GoogleIdTokenVerifier = GoogleIdTokenVerifier.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
    .setAudience(listOf(securityConfig[SecuritySpec.clientId]))
    .build()

//val GoogleIdToken.Payload.userId get() = UserID(subject) TODO: Add when deprecated conflict is removed from Google library

/**
 * Verifies a user session. If the session's token is invalid, attempt to refresh it.
 *
 * @return a valid [GoogleIdToken] if the user session is valid (or becomes valid after refresh); otherwise, null.
 */
suspend fun GoogleIdTokenVerifier.verifyUserSessionIdToken(
    userSession: UserSession,
    sessions: CurrentSession,
    refresh: Boolean = true
): GoogleIdToken? {

    return verify(userSession.idToken) ?: if (refresh) {
        val newSession = refreshUserSessionTokens(oldSession = userSession, sessions = sessions) ?: return null
        verifyUserSessionIdToken(userSession = newSession, sessions = sessions, refresh = false)
    } else null
}

/**
 * Attempts to refresh the idToken of the current [UserSession].
 *
 * @return the new, refreshed [GoogleIdToken], or null if unable to refresh
 */
private suspend fun refreshUserSessionTokens(oldSession: UserSession, sessions: CurrentSession): UserSession? {
    val tokens = getRefreshedGoogleTokens(oldSession.refreshToken) ?: return null
    val newSession = oldSession.copy(idToken = tokens.idToken)

    sessions.set(newSession)
    return newSession
}

/**
 * A class holding the id and refresh token returned by the Google API.
 */
private data class GoogleTokens(
    val accessToken: String,
    val idToken: String
)

private suspend fun getRefreshedGoogleTokens(refreshToken: String): GoogleTokens? {
    println("Refreshing token")
    val httpClient: HttpClient = getKoin().get()
    val tokenInfo: TokenInfo = httpClient.submitForm(
        url = "https://accounts.google.com/o/oauth2/token",
        formParameters = parameters {
            append("grant_type", "refresh_token")
            append("client_id", securityConfig[SecuritySpec.clientId])
            append("client_secret", securityConfig[SecuritySpec.clientSecret])
            append("refresh_token", refreshToken)
        }
    )
        .apply { if (!status.isSuccess()) return null }
        .body()

    return GoogleTokens(tokenInfo.accessToken, tokenInfo.idToken)
}

@Serializable
private data class TokenInfo(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("id_token") val idToken: String,
)