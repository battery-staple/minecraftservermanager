package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.versionType
import java.io.IOException
import kotlin.io.path.*

class LocalFilesystemMinecraftServerJarRepository(private val directoryName: String) : MinecraftServerJarRepository {
    override fun getJar(version: MinecraftVersion): MinecraftServerJar? =
        directory.useDirectoryEntries { entries ->
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

    override fun saveJar(jar: MinecraftServerJar): MinecraftServerJar? =
        // TODO: Validate jar somehow
        try {
            val name = JarFileName(jar.version)

            jar.copy(
                path = jar.path.moveTo(directory / "$name.jar")
            )
        } catch (e: IOException) {
            println("Got exception when trying to save jar: ${e.message}")
            null
        }

    override fun deleteJar(version: MinecraftVersion): Boolean =
        try {
            directory.useDirectoryEntries { entries ->
                entries.filter { entryPath ->
                    entryPath.nameWithoutExtension == version.versionString
                }
            }.all {
                it.deleteIfExists() // Not transactional; TODO: is that bad?
            }
        } catch (e: IOException) {
            false
        }

    private val directory get() = Path(directoryName)
        .also { it.createDirectories() }

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