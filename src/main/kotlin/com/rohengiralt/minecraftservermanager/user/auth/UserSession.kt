package com.rohengiralt.minecraftservermanager.user.auth

/**
 * Data class representing the current user as stored in the session
 */
data class UserSession(
    /**
     * The user's Google refresh token
     */
    val refreshToken: String?,
    /**
     * The user's current Google identity token
     */
    val idToken: String
)