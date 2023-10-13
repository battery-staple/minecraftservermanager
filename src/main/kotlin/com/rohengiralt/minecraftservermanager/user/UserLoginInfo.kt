package com.rohengiralt.minecraftservermanager.user

import com.rohengiralt.minecraftservermanager.user.UserID.Companion.MAX_LENGTH
import io.ktor.server.auth.*

/**
 * A principal representing some basic user data
 */
data class UserLoginInfo(
    val userId: UserID,
    val email: String
) : Principal

/**
 * A string unique to a user. Currently implemented as the user's Google ID.
 */
@JvmInline
value class UserID(
    /**
     * The user's Google ID. At most [MAX_LENGTH] characters.
     */
    val idString: String
) {
    init {
        require(idString.length <= MAX_LENGTH) { "User ID cannot have size greater than $MAX_LENGTH"}
    }

    companion object {
        /**
         * The greatest valid length of [idString]
         */
        const val MAX_LENGTH = 255
    }
}