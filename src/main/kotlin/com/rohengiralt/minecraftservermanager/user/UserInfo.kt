package com.rohengiralt.minecraftservermanager.user

import io.ktor.server.auth.*

data class UserInfo(
    val userId: UserID,
    val email: String
) : Principal

@JvmInline
value class UserID(val idString: String)