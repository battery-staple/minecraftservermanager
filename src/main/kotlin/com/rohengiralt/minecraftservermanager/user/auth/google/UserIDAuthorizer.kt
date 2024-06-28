package com.rohengiralt.minecraftservermanager.user.auth.google

import com.rohengiralt.minecraftservermanager.plugins.SecuritySpec
import com.rohengiralt.minecraftservermanager.plugins.securityConfig
import com.rohengiralt.minecraftservermanager.user.UserID
import org.slf4j.LoggerFactory
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.streams.asSequence

interface UserIDAuthorizer {
    fun isAuthorized(userId: UserID): Boolean
}

class WhitelistFileUserIDAuthorizer : UserIDAuthorizer {
    private val whitelistFile = Path(securityConfig[SecuritySpec.whitelistFile])

    private val logger = LoggerFactory.getLogger(this.javaClass)
    init {
        if (!whitelistFile.exists()) {
            logger.error("Could not find whitelist file at `$whitelistFile`. Aborting.")
            throw IllegalStateException("Could not find whitelist file")
        }
    }

    override fun isAuthorized(userId: UserID): Boolean = try {
        whitelistFile
            .bufferedReader()
            .lines()
            .asSequence()
            .any { it.trim() == userId.idString }
    } catch (e: Throwable) {
        logger.error("Could not read whitelist file.")
        throw IllegalStateException("Could not read whitelist file")
    }
}