//package com.rohengiralt.minecraftservermanager.server
//
//import com.rohengiralt.minecraftservermanager.ServerRuntimeDirectoryRepository
//import com.rohengiralt.minecraftservermanager.model.Port
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.get
//import org.koin.core.component.inject
//import java.util.*
//
//class MinecraftServerFactory : KoinComponent {
//    private val serverJarRepository: ServerJarRepository by inject()
//    private val serverRuntimeDirectoryRepository: ServerRuntimeDirectoryRepository by inject()
//
//    suspend fun createServer(uuid: UUID, name: String, version: String, port: Port): MinecraftServer =
//        MinecraftServer(
//            uuid = uuid,
//            name = name,
//            version = version,
//            port = port,
//            runtimeConfiguration = MinecraftServerRuntimeConfiguration(
//                jar = serverJarRepository.getJar(version),
//                contentDirectory = serverRuntimeDirectoryRepository.getRuntimeDirectory(name + uuid.toString())
//            ),
//            get()
//        )
//
//    suspend fun MinecraftServerDisplay.createServer(): MinecraftServer? = //TODO: Move to context extension function
//        if (id != null && name != null && version != null && port != null) {
//           createServer(uuid = id, name = name, version = version, port = port)
//        } else null
//
//}