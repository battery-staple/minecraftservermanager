package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.MinecraftJarAPI
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

class APIMinecraftServerJarFactory : MinecraftServerJarFactory, KoinComponent {
    override suspend fun newJar(version: MinecraftVersion): MinecraftServerJar? {
        logger.debug("Downloading server jar with version ${version.versionString}")

        val jarPath = directory / "${version.versionString}.jar"

        val success = withContext(Dispatchers.IO) {
            jarPath.deleteIfExists()
            writeServerJarTo(jarPath, version)
        }

        return if (success) {
            MinecraftServerJar(jarPath, version)
        } else null
    }

    /**
     * Writes a jar for the Minecraft version [version] to [path], overwriting [path].
     * Precondition: no file may exist at [path].
     * @return true if successful, false if not.
     */
    private suspend fun writeServerJarTo(path: Path, version: MinecraftVersion): Boolean {
        path.createFile()

        try {
            val success = jarAPI.appendServerToPath(path, version)
            if (!success) path.deleteIfExists()

            return success
        } catch (e: Exception) {
            path.deleteIfExists()
            throw e
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