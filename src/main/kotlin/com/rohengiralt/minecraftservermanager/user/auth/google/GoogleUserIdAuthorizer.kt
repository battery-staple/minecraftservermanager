package com.rohengiralt.minecraftservermanager.user.auth.google

import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.streams.asSequence

interface GoogleUserIdAuthorizer {
    fun isAuthorized(userId: UserID): Boolean
}

class WhitelistFileGoogleUserIdAuthorizer : GoogleUserIdAuthorizer {
    private val whitelistFile = Path(securityConfig[SecuritySpec.whitelistFile])
    override fun isAuthorized(userId: UserID): Boolean = try {
        whitelistFile
            .bufferedReader()
            .lines()
            .asSequence()
            .any { it.trim() == userId.idString }
    } catch (e: Throwable) {
        false
    }
}