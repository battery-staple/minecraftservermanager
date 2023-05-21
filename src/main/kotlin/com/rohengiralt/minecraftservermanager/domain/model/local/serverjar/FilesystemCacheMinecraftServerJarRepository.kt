package com.rohengiralt.minecraftservermanager.domain.model.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.versionType
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import kotlin.io.path.*

class FilesystemCacheMinecraftServerJarRepository(private val directoryName: String) : MinecraftServerJarRepository, KoinComponent {
    override suspend fun getJar(version: MinecraftVersion): MinecraftServerJar =
        getCachedJar(version)
            ?: jarFactory.newJar(version).let { newJar ->
                cacheJar(newJar) ?: newJar
            }

    private fun getCachedJar(version: MinecraftVersion): MinecraftServerJar? {
        println("Trying to get cached jar for version ${version.versionString}")
        return directory.useDirectoryEntries { entries ->
            entries
                .map {
                    it to JarFileName.fromString(it.nameWithoutExtension)
                }
                .firstOrNull { (_, name) ->
                    version == name?.version
                }
        }?.let { (path, name) ->
            name ?: return null
            MinecraftServerJar(path, version)
        }
    }

    private fun cacheJar(jar: MinecraftServerJar): MinecraftServerJar? {
        println("Caching new jar with version ${jar.version.versionString}")
        return try {
            val name = JarFileName(jar.version)

            jar.copy(
                path = jar.path.moveTo(directory / "$name.jar")
            )
        } catch (e: IOException) {
            println("Got exception when trying to save jar: ${e.message}")
            null
        }
    }

    override suspend fun deleteJar(version: MinecraftVersion): Boolean {
        println("Deleting jar for version ${version.versionString}")
        return try {
            directory.useDirectoryEntries { entries ->
                entries.filter { entryPath ->
                    entryPath.nameWithoutExtension == version.versionString
                }
            }.all {
                it.deleteIfExists() // Not transactional; TODO: is that bad?
            }
        } catch (e: IOException) {
            println("Got exception when trying to delete jar: ${e.message}")
            false
        }
    }

    private val directory get() = Path(directoryName)
        .also { it.createDirectories() }

    private val jarFactory: MinecraftServerJarFactory by inject()
    private data class JarFileName(val version: MinecraftVersion) {
        override fun toString(): String =
            "${version.versionString}---${version.versionType}"

        companion object {
            private val parsingRegex = "(.*)---(.*)".toRegex()

            @JvmStatic
            fun fromString(string: String): JarFileName? {
                val (versionString, versionTypeString) = parsingRegex.matchEntire(string)?.destructured ?: return null

                return JarFileName(
                    MinecraftVersion.fromString(
                        versionString,
                        MinecraftVersion.VersionType.valueOf(versionTypeString)
                    ) ?: return null
                )
            }

        }
    }
}