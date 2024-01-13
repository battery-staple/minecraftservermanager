package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.MinecraftJarAPI
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.path.clear
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div

class APIMinecraftServerJarFactory : MinecraftServerJarFactory, KoinComponent {
    override suspend fun newJar(version: MinecraftVersion): MinecraftServerJar {
        logger.debug("Downloading server jar with version ${version.versionString}")
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

    private suspend fun appendServerToOrDelete(path: Path, version: MinecraftVersion) {
        try {
            jarAPI.appendServerToPath(path, version)
        } catch (e: Throwable) {
            path.deleteIfExists()
            throw e // TODO: Should return null instead?
        }
    }

    private val jarAPI: MinecraftJarAPI by inject()

    private val directory get() = Path(DIRECTORY_PATH_STRING)
        .also { it.createDirectories() }

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val DIRECTORY_PATH_STRING = "/minecraftservermanager/tmp/local/jars/downloaded" // TODO: Clean up occasionally
    }
}