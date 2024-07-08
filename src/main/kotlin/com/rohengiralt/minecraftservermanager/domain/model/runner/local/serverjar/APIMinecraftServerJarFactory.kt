package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.MinecraftJarAPI
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.*

class APIMinecraftServerJarFactory : MinecraftServerJarFactory, KoinComponent {
    /**
     * Controls access to [newJar].
     * Only one new jar can be created at once because otherwise multiple jobs may attempt to overwrite the same jar.
     */
    private val newJarMutex = Mutex()

    override suspend fun newJar(version: MinecraftVersion): MinecraftServerJar? = newJarMutex.withLock {
        logger.debug("Downloading server jar with version ${version.versionString}")

        val jarPath = directory / "${version.versionString}.jar"

        val success = withContext(Dispatchers.IO) {
            jarPath.deleteIfExists()
            writeServerJarTo(jarPath, version)
        }

        if (success) {
            logger.trace("Successfully downloaded server jar with version ${version.versionString}")
            MinecraftServerJar(jarPath, version)
        } else {
            logger.trace("Failed to download server jar with version ${version.versionString}")
            null
        }
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
            if (!success) {
                logger.debug("Failed to append server to path {}; deleting.", path)
                path.deleteIfExists()
            }

            return success
        } catch (e: Exception) {
            logger.debug("Failed to append server to path $path; deleting.", e)
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