package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import kotlinx.serialization.Serializable

@Serializable
data class UserLoginInfoAPIModel(
    val userID: String,
    val email: String,
) {
    constructor(userLoginInfo: UserLoginInfo) : this(
        userID = userLoginInfo.userId.idString,
        email = userLoginInfo.email
    )

    fun toUserLoginInfo(): UserLoginInfo =
        UserLoginInfo(UserID(userID), email)
}