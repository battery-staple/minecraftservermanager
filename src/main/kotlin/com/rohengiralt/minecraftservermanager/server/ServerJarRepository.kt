//package com.rohengiralt.minecraftservermanager.server
//
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.inject
//import org.koin.dsl.module
//import java.nio.file.Path
//import kotlin.io.path.*
//
//interface ServerJarRepository {
//    suspend fun getJar(version: String): Path
//    suspend fun saveJar(version: String, jar: Path)
//
//    companion object {
//        val koinModule = module {
//            single<ServerJarRepository> { ServerJarRepositoryImpl() }
//        }
//    }
//}
//
//private class ServerJarRepositoryImpl : ServerJarRepository, KoinComponent {
//    private val jarFactory: ServerJarFactory by inject()
//
//    private val directoryPath = "/minecraftservermanager/jars"
//    private val directory = Path(directoryPath)
//        .also { it.createDirectories() }
//
//    override suspend fun getJar(version: String): Path =
//        getDefaultJar(version)
//            ?: getCustomJar(version)
//            ?: createJar(version) //TODO: Should create jar?
//
//    override suspend fun saveJar(version: String, jar: Path) {
//        TODO()
////        jar.renameTo(File(customDirectory + ""))
//    }
//
//    private suspend fun createJar(version: String): Path {
//        TODO("removed")
////        println("Creating server jar with version $version")
////        return (defaultDirectory / "$version.jar")
////            .apply {
////                @Suppress("BlockingMethodInNonBlockingContext") // Using Dispatchers.IO
////                withContext(Dispatchers.IO) {
////                    deleteIfExists()
////                    createFile()
////                    try {
////                        with(jarFactory) {
////                            appendServerContents(version)
////                        }
////                    } catch (e: Throwable) {
////                        deleteIfExists()
////                        throw e
////                    }
////                }
////            }
//    }
//
//    private val defaultDirectory = (directory / "default")
//        .also { it.createDirectories() }
//
//    private fun getDefaultJar(version: String): Path? =
//        defaultDirectory.useDirectoryEntries { entries ->
//            entries.firstOrNull {
//                it.nameWithoutExtension == version
//            }
//        }
//
//    private val customDirectory = (directory / "custom")
//        .also { it.createDirectories() }
//
//    private fun getCustomJar(version: String): Path? =
//        customDirectory.useDirectoryEntries { entries ->
//            entries.firstOrNull {
//                it.nameWithoutExtension == version
//            }
//        }
//}