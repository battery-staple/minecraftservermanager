package com.rohengiralt.minecraftservermanager.auth

import io.ktor.server.auth.*

data class UserSession(val state: String, val token: String, val refreshToken: String) : Principal