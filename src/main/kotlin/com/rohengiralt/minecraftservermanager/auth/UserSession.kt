package com.rohengiralt.minecraftservermanager.auth

import io.ktor.server.auth.*

data class UserSession(val refreshToken: String, val idToken: String) : Principal