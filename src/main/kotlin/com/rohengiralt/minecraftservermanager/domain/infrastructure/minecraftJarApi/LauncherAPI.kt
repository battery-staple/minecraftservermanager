package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.appendGetToPath
import com.rohengiralt.minecraftservermanager.util.ifNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path

class LauncherAPI : MinecraftJarAPI, KoinComponent {
    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean {
        val manifest = getManifest().ifNull {
            logger.warn("Couldn't get manifest")
            return false
        }
        val versionURL = getVersionURL(version, manifest).ifNull {
            logger.warn("Couldn't find jar of version ${version.versionString} in manifest")
            return false
        }
        val launcher = getLauncher(versionURL).ifNull {
            logger.warn("Couldn't get launcher json")
            return false
        }
        val serverURL = launcher.serverURL.ifNull {
            logger.warn("Couldn't find server jar for version ${version.versionString}")
            return false
        }

        return httpClient.appendGetToPath(serverURL, path)
    }

    private suspend fun getManifest(): Manifest? =
        httpClient
            .get(MANIFEST_URL)
            .bodyOrNull<Manifest?>()

    private fun getVersionURL(version: MinecraftVersion, manifest: Manifest): String? =
        manifest.versions.firstOrNull {
            it.id == version.versionString
        }?.url

    private suspend fun getLauncher(url: String): Launcher? =
        httpClient
            .get(url)
            .bodyOrNull()

    private val Launcher.serverURL get(): String? = downloads.server?.url

    private infix fun Path.matchesHash(sha1: String): Boolean = true //TODO: Implement and use

    private suspend inline fun <reified T> HttpResponse.bodyOrNull(): T? =
        if (status.isSuccess()) body() else null

    private val httpClient: HttpClient by inject()

    @Serializable
    data class Manifest(val versions: List<ManifestVersion>)

    @Serializable
    data class ManifestVersion(val id: String, val type: String, val url: String/*, val time: String, val releaseTime: String*/)

    @Serializable
    data class Launcher(val downloads: Downloads)

    @Serializable
    data class Downloads(val client: Download? = null, val server: Download? = null)

    @Serializable
    data class Download(val sha1: String, val url: String)

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    }
}