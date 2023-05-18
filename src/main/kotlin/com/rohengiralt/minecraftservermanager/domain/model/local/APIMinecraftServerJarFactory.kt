package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.MinecraftJarAPI
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.nio.file.Path
import kotlin.io.path.*

class APIMinecraftServerJarFactory : MinecraftServerJarFactory, KoinComponent {
    override suspend fun newJar(version: MinecraftVersion): MinecraftServerJar {
        println("Downloading server jar with version ${version.versionString}")
        return (directory / "${version.versionString}.jar")
            .also { path ->
                withContext(Dispatchers.IO) {
                    path.clear()
                    appendServerToOrDelete(path, version)
                }
            }
            .let { path ->
                MinecraftServerJar(path, version)
            }
    }

    private fun Path.clear() {
        deleteIfExists()
        createFile()
    }

    @Suppress("BlockingMethodInNonBlockingContext") // Must be run in Dispatchers.IO
    private suspend fun appendServerToOrDelete(path: Path, version: MinecraftVersion) {
        try {
            jarAPI.appendServerToPath(path, version)
        } catch (e: Throwable) {
            path.deleteIfExists()
            throw e
        }
    }

    private val jarAPI: MinecraftJarAPI by inject()

    private val directory get() = Path(DIRECTORY_PATH_STRING)
        .also { it.createDirectories() }

    companion object {
        private const val DIRECTORY_PATH_STRING = "/minecraftservermanager/tmp/local/jars/downloaded" // TODO: Clean up occasionally
    }
}