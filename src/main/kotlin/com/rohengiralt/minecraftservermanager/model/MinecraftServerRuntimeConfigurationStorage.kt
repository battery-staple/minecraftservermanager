//package com.rohengiralt.minecraftservermanager.model
//
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerRuntimeConfiguration
//import com.rohengiralt.minecraftservermanager.util.extensions.path.PathSerializer
//import kotlinx.serialization.Serializable
//import java.nio.file.Path
//
//@Serializable
//data class MinecraftServerRuntimeConfigurationStorage(
//    @Serializable(with = PathSerializer::class) val jar: Path,
//    @Serializable(with = PathSerializer::class) val contentDirectory: Path,
//) {
//    constructor(serverRuntimeConfiguration: MinecraftServerRuntimeConfiguration) :
//            this(
//                serverRuntimeConfiguration.jar,
//                serverRuntimeConfiguration.contentDirectory
//            )
//}