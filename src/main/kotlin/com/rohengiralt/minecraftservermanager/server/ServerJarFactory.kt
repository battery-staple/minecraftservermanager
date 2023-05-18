//package com.rohengiralt.minecraftservermanager.server
//
//import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
//import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.appendGetToFile
//import com.rohengiralt.minecraftservermanager.util.ifTrue.ifFalse
//import io.ktor.client.*
//import io.ktor.client.call.*
//import io.ktor.client.request.*
//import io.ktor.http.*
//import kotlinx.serialization.Serializable
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.inject
//import org.koin.dsl.module
//import java.nio.file.Path
//
//// Legacy class
//interface ServerJarFactory {
//    suspend fun Path.appendServerContents(version: MinecraftVersion)
//
//    companion object {
//        val koinModule = module {
//            single<ServerJarFactory> {
//                LauncherAPIServerJarFactory()
//            }
//        }
//    }
//}
//
//private class LauncherAPIServerJarFactory : ServerJarFactory, KoinComponent {
//    private val client: HttpClient by inject()
//    override suspend fun Path.appendServerContents(version: MinecraftVersion) {
//        val manifest: Manifest =
//            client
//                .get(MANIFEST_URL)
//                .let { httpResponse ->
//                    check(httpResponse.status.isSuccess()) {
//                        println("Couldn't get manifest, got response $httpResponse")
//                        "Cannot get manifest"
//                    }
//
//                    httpResponse.body()
//                }
//
//        val versionUrl = manifest.versions.firstOrNull {
//            it.id == version.versionString
//        }?.url ?: run {
//            println("Couldn't find jar of version ${version.versionString} in manifest")
//            error("No such version ${version.versionString}")
//        }
//
//        val launcher: Launcher = client.get(versionUrl)
//            .let { httpResponse ->
//                check(httpResponse.status.isSuccess()) {
//                    println("Couldn't get launcher json, got response $httpResponse")
//                    "Cannot get launcher json"
//                }
//
//                httpResponse.body()
//            }
//
//        val server = launcher.downloads.server ?: kotlin.run {
//            println("Couldn't find server jar for version ${version.versionString}")
//            error("No server for version ${version.versionString}")
//        }
//
//        //TODO: check hash
//
//        client.appendGetToFile(server.url, this).ifFalse {
//            println("Couldn't get jar of version ${version.versionString}")
//            error("No such version ${version.versionString}")
//        }
//    }
//
//    @Serializable
//    data class Manifest(val versions: List<Versions>)
//
//    @Serializable
//    data class Versions(val id: String/*, val type: String*/, val url: String/*, val time: String, val releaseTime: String*/)
//
//    @Serializable
//    data class Launcher(val downloads: Downloads)
//
//    @Serializable
//    data class Downloads(val server: Download? = null)
//
//    @Serializable
//    data class Download(val sha1: String, val url: String)
//
//    companion object {
//        private const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
//    }
//}
//
//private class AWSAPIServerJarFactory : ServerJarFactory, KoinComponent {
//    private val client: HttpClient by inject()
//
//    override suspend fun Path.appendServerContents(version: MinecraftVersion) {
//        client.appendGetToFile("https://s3.amazonaws.com/Minecraft.Download/versions/${version.versionString}/minecraft_server.${version.versionString}.jar", this).ifFalse {
//            println("Couldn't get jar of version ${version.versionString}")
//            throw IllegalArgumentException("No such version ${version.versionString}")
//        }
//    }
//}