//package com.rohengiralt.minecraftservermanager.domain.model.local
//
//import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
//import com.rohengiralt.minecraftservermanager.server.ServerJarFactory
//import io.ktor.client.*
//import io.ktor.client.call.*
//import io.ktor.client.request.*
//import io.ktor.client.statement.*
//import io.ktor.http.*
//import io.ktor.utils.io.*
//import io.ktor.utils.io.core.*
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import kotlinx.serialization.Serializable
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.inject
//import java.nio.file.Path
//import kotlin.io.path.appendBytes
//
//private class LauncherAPIServerJarFactory : ServerJarFactory, KoinComponent {
//    private val client: HttpClient by inject()
//    override suspend fun Path.appendServerContents(version: MinecraftVersion) {
//        val manifest: Manifest =
//            client
//                .get<HttpStatement>(MANIFEST_URL)
//                .execute { httpResponse ->
//                    if (!httpResponse.status.isSuccess()) {
//                        println("Couldn't get manifest, got response $httpResponse")
//                        throw IllegalStateException("Cannot get manifest")
//                    }
//
//                    httpResponse.receive()
//                }
//
//        val versionUrl: String = manifest.versions.firstOrNull {
//            it.id == version.versionString()
//        }?.url ?: run {
//            println("Couldn't find jar of version $version in manifest")
//            throw IllegalArgumentException("No such version $version")
//        }
//
//        val launcher: Launcher = client.get<HttpStatement>(versionUrl)
//            .execute { httpResponse ->
//                if (!httpResponse.status.isSuccess()) {
//                    println("Couldn't get launcher json, got response $httpResponse")
//                    throw IllegalStateException("Cannot get launcher json")
//                }
//
//                httpResponse.receive()
//            }
//
//        val server: Download = launcher.downloads.server ?: kotlin.run {
//            println("Couldn't find server jar for version $version")
//            throw IllegalArgumentException("No server for version $version")
//        }
//
//        //TODO: check hash
//
//        client.appendGetToFile(server.url, this) {
//            println("Couldn't get jar of version $version, got response $it")
//            throw IllegalArgumentException("No such version $version")
//        }
//    }
//
//    @Serializable
//    data class Manifest(val versions: List<Version>)
//
//    @Serializable
//    data class Version(val id: String, val type: String, val url: String/*, val time: String, val releaseTime: String*/)
//
//    @Serializable
//    data class Launcher(val downloads: Downloads)
//
//    @Serializable
//    data class Downloads(val client: Download? = null, val server: Download? = null)
//
//    @Serializable
//    data class Download(val sha1: String, val url: String)
//
//    companion object {
//        private const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
//    }
//}
//
//private suspend inline fun HttpClient.appendGetToFile(urlString: String, file: Path, crossinline onFailure: (HttpResponse) -> Unit) {
//    get<HttpStatement>(urlString)
//        .execute { httpResponse ->
//            if (!httpResponse.status.isSuccess()) onFailure(httpResponse)
//
//            val channel: ByteReadChannel = httpResponse.receive()
//            println("Writing to file")
//            while (!channel.isClosedForRead) {
//                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
//
//                @Suppress("BlockingMethodInNonBlockingContext")
//                withContext(Dispatchers.IO) {
//                    while (packet.isNotEmpty) {
//                        val bytes = packet.readBytes()
//                        file.appendBytes(bytes)
//                    }
//                }
//            }
//            println("Appended content to file")
//        }
//}