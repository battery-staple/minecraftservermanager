package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.appendGetToPath
import com.rohengiralt.minecraftservermanager.util.ifTrue.ifFalse
import io.ktor.client.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path

class AWSAPI : MinecraftJarAPI, KoinComponent {
    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean {
        logger.debug("Appending server with version {} to path {}", version.versionString, path)
        httpClient.appendGetToPath(
            "https://s3.amazonaws.com/Minecraft.Download/versions/${version.versionString}/minecraft_server.${version.versionString}.jar", path
        ).ifFalse {
            logger.warn("Couldn't get jar of version ${version.versionString}")
            return false
        }

        logger.trace("Successfully got jar of version ${version.versionString}")
        return true
    }

    private val httpClient: HttpClient by inject()
    private val logger = LoggerFactory.getLogger(this::class.java)
}