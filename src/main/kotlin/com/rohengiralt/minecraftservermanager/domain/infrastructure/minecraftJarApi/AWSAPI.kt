package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.appendGetToFile
import com.rohengiralt.minecraftservermanager.util.ifTrue.ifFalse
import io.ktor.client.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Path

class AWSAPI : MinecraftJarAPI, KoinComponent {
    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean {
        httpClient.appendGetToFile(
            "https://s3.amazonaws.com/Minecraft.Download/versions/${version.versionString}/minecraft_server.${version.versionString}.jar", path
        ).ifFalse {
            println("Couldn't get jar of version ${version.versionString}")
            return false
        }
        return true
    }

    private val httpClient: HttpClient by inject()
}