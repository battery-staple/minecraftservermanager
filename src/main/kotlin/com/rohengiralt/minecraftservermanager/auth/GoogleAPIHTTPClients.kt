import com.rohengiralt.minecraftservermanager.auth.UserSession
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientId
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec.clientSecret
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.sessions.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class GoogleAPIHTTPClients {
    private val cachedClients = mutableMapOf<String, HttpClient>()

    fun getClient(currentSession: CurrentSession? = null): HttpClient {
        val userSession = currentSession?.get<UserSession>() ?: return defaultClient

        return cachedClients.getOrPut(userSession.refreshToken) {
            HttpClient(OkHttp) {
                engine {
                    config {
                        followRedirects(true)
                    }
                }

                install(ContentNegotiation) {
                    json()
                }

                install(Auth) {
                    bearer {
                        loadTokens {
                            BearerTokens(userSession.token, userSession.refreshToken)
                        }

                        sendWithoutRequest { request ->
                            request.url.host in listOf("www.googleapis.com", "accounts.google.com")
                        }

                        refreshTokens {
                            val refreshTokenInfo: TokenInfo = client.submitForm(
                                url = "https://accounts.google.com/o/oauth2/token",
                                formParameters = parameters {
                                    append("grant_type", "refresh_token")
                                    append("client_id", securityConfig[clientId])
                                    append("client_secret", securityConfig[clientSecret])
                                    append("refresh_token", oldTokens?.refreshToken ?: "")
                                }
                            ) { markAsRefreshTokenRequest() }.body()


                            val newSession = userSession.copy(token = refreshTokenInfo.accessToken)
                            currentSession.set(newSession)
                            return@refreshTokens BearerTokens(
                                accessToken = newSession.token,
                                refreshToken = newSession.refreshToken
                            )
                        }
                    }
                }
            }
        }
    }

    private val defaultClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    followRedirects(true)
                }
            }

            install (ContentNegotiation) {
                json()
            }
        }
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
}