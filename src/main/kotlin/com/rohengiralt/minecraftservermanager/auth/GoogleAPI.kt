package com.rohengiralt.minecraftservermanager.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.java.KoinJavaComponent.getKoin

suspend fun GoogleIdTokenVerifier.verifyUserSessionIdToken(sessions: CurrentSession, refresh: Boolean = true): GoogleIdToken? {
    val userSession = sessions.get<UserSession>() ?: return null

    return verify(userSession.idToken) ?: if (refresh) {
        refreshUserSessionTokens(sessions)
        verifyUserSessionIdToken(sessions = sessions, refresh = false)
    } else null

}

suspend fun refreshUserSessionTokens(sessions: CurrentSession): Boolean {
    val oldSession = sessions.get<UserSession>() ?: return false
    val tokens = getRefreshedGoogleTokens(oldSession.refreshToken) ?: return false
    sessions.set(oldSession.copy(idToken = tokens.idToken))
    return true
}

data class GoogleTokens(
    val accessToken: String,
    val idToken: String
)

suspend fun getRefreshedGoogleTokens(refreshToken: String): GoogleTokens? {
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