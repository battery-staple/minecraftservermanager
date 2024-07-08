package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.appendGetToPath
import com.rohengiralt.minecraftservermanager.util.ifTrue.ifFalse
import io.ktor.client.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * A [MinecraftJarAPI] that pulls from [BetaCraft](https://betacraft.uk)
 */
class BetaCraftAPI : MinecraftJarAPI, KoinComponent {
    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean {
        val endpoint = BetaCraftEndpoint.fromVersion(version) ?: return false

        logger.debug("Appending server with version {} to path {}", version.versionString, path)
        httpClient.appendGetToPath(
            endpoint.url, path
        ).ifFalse {
            logger.warn("Couldn't get jar of version ${version.versionString}")
            return false
        }

        logger.trace("Successfully got jar of version ${version.versionString}")
        return true
    }

    private val httpClient: HttpClient by inject()
}

/**
 * Represents an endpoint for a server jar in the BetaCraft API.
 */
private class BetaCraftEndpoint(private val phase: String, private val version: String) {
    /**
     * The URL of this endpoint
     */
    val url: String get() = "https://files.betacraft.uk/server-archive/$phase/$version.jar"

    companion object {
        fun fromVersion(version: MinecraftVersion): BetaCraftEndpoint? {
            val phase: String
            val versionString: String
            when (version) {
                is MinecraftVersion.Vanilla.Alpha -> {
                    phase = "alpha"
                    versionString = "a" + version.versionString
                }
                is MinecraftVersion.Vanilla.Beta -> {
                    phase = "beta"
                    versionString = "b" + version.versionString
                }
                is MinecraftVersion.Vanilla.Classic -> {
                    phase = "classic"
                    versionString = "c" + version.versionString
                }
                else -> {
                    logger.warn("Unsupported version type $version")
                    return null
                }
            }

            return BetaCraftEndpoint(phase, versionString)
        }
    }
}

//fun serverVersion(clientVersion: MinecraftVersion.Vanilla.Alpha) {
//    val (phase, major, minor, patch) = clientVersion
//    when {
//        phase == 1u && major == 0u && minor == 15u && patch == null -> "0.1.0"
//        phase == 1u && major == 0u && minor == 16u && patch == null -> "0.1.1"
//        phase == 1u && major == 0u && minor == 16u && patch == 1u -> "0.1.2_01" // 0.1.2 also exists, but has auth bug (https://minecraft.fandom.com/wiki/Java_Edition_Alpha_server_0.1.2_01)
//        phase == 1u && major == 0u && minor == 16u && patch == 2u -> "0.1.3"
//        phase == 1u && major == 0u && minor == 17u -> "0.1.4" // covers multiple patch values
//        phase == 1u && major == 1u && minor == 0u && patch == null -> "0.2.0_01" // 0.2.0 also exists, but has bug (https://minecraft.fandom.com/wiki/Java_Edition_Alpha_server_0.2.0_01)
//        phase == 1u && major == 1u && 0u <= minor && minor <= 2u -> "0.2.1" // covers multiple versions
//        phase == 1u && major == 2u && minor == 0u -> "0.2.2_01" // 0.2.2 also exists, but has bug (https://minecraft.fandom.com/wiki/Java_Edition_Alpha_server_0.2.2_01)
//        phase == 1u && major == 2u && minor == 1u && patch == 2u -> "0.2.3"
//    }
//}

private val logger = LoggerFactory.getLogger(BetaCraftAPI::class.java)