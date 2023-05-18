package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.appendGetToFile
import com.rohengiralt.minecraftservermanager.util.ifNull
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Path

class LauncherAPI : MinecraftJarAPI, KoinComponent {
    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean {
        val manifest = getManifest().ifNull {
            println("Couldn't get manifest")
            throw IllegalStateException("Cannot get manifest")
        }
        val versionURL = getVersionURL(version, manifest).ifNull {
            println("Couldn't find jar of version ${version.versionString} in manifest")
            throw IllegalArgumentException("No such version ${version.versionString}")
        }
        val launcher = getLauncher(versionURL).ifNull {
            println("Couldn't get launcher json")
            throw IllegalStateException("Cannot get launcher json")
        }
        val serverURL = launcher.serverURL.ifNull {
            println("Couldn't find server jar for version ${version.versionString}")
            throw IllegalArgumentException("No server for version ${version.versionString}")
        }

        return httpClient.appendGetToFile(serverURL, path)
    }

    private suspend fun getManifest(): Manifest? =
        httpClient
            .get(MANIFEST_URL)
            .bodyOrNull()

    private suspend fun getVersionURL(version: MinecraftVersion, manifest: Manifest): String? =
        manifest.versions.firstOrNull {
            it.id == version.versionString
        }?.url

    private suspend fun getLauncher(url: String): Launcher? =
        httpClient
            .get(url)
            .bodyOrNull()

    private val Launcher.serverURL get(): String? = downloads.server?.url

    private infix fun Path.matchesHash(sha1: String): Boolean = true //TODO: Implement

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

    companion object {
        private const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    }
}