package com.rohengiralt.minecraftservermanager.auth

import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.streams.asSequence

interface GoogleUserIdAuthorizer {
    fun isAuthorized(userId: String): Boolean
}

class WhitelistFileGoogleUserIdAuthorizer : GoogleUserIdAuthorizer {
    private val whitelistFile = Path(securityConfig[SecuritySpec.whitelistFile])
    override fun isAuthorized(userId: String): Boolean =
        whitelistFile
            .bufferedReader()
            .lines()
            .asSequence()
            .any { it.trim() == userId }
}