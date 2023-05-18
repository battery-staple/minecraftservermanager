package com.rohengiralt.minecraftservermanager

import org.koin.dsl.module
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.div

interface ServerRuntimeDirectoryRepository {
    fun getRuntimeDirectory(name: String): Path

    companion object {
        val koinModule = module {
            single<ServerRuntimeDirectoryRepository> {
                ServerRuntimeDirectoryRepositoryImpl()
            }
        }
    }
}

private class ServerRuntimeDirectoryRepositoryImpl : ServerRuntimeDirectoryRepository {
    private val directoryPath = Path("/minecraftservermanager/servers") //TODO: Configurable
        .also {
            it.createDirectories()
        }

    override fun getRuntimeDirectory(name: String): Path =
        (directoryPath/name)
            .apply {
                toFile().deleteRecursively()
                createDirectory()
            }
}