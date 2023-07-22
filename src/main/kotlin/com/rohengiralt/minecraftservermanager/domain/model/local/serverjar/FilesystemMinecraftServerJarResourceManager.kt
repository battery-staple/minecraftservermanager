package com.rohengiralt.minecraftservermanager.domain.model.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.versionType
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.util.*
import kotlin.io.path.*

class FilesystemMinecraftServerJarResourceManager(private val directoryName: String) : MinecraftServerJarResourceManager, KoinComponent {
    override suspend fun prepareJar(version: MinecraftVersion): Boolean = // TODO: Delete after some time?
        if (getCachedJar(version) != null) {
            true
        } else {
            cacheNewJar(version) != null
        }

    override suspend fun accessJar(version: MinecraftVersion, accessorKey: UUID): MinecraftServerJar? {
        if (!prepareJar(version)) return null
        jarReferenceCounter.addReference(version, accessorKey)

        return getCachedJar(version)
    }

    override suspend fun freeJar(version: MinecraftVersion, accessorKey: UUID): Boolean {
        jarReferenceCounter.removeReference(version, accessorKey)
        return true
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

    private suspend fun cacheNewJar(version: MinecraftVersion): MinecraftServerJar? =
        jarFactory.newJar(version).let(::cacheJar)

    private fun cacheJar(jar: MinecraftServerJar): MinecraftServerJar? {
        println("Caching new jar with version ${jar.version.versionString}")
        return try {
            val name = JarFileName(jar.version)

            jar.copy(
                path = jar.path.moveTo(directory / "$name.jar", overwrite = true)
            )
        } catch (e: IOException) {
            println("Got exception when trying to save jar: ${e.message}")
            null
        }
    }

    private fun deleteJar(version: MinecraftVersion): Boolean {
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

    private val jarReferenceCounter = JarReferenceCounter(::deleteJar)

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

private class JarReferenceCounter(private val deleteJar: (version: MinecraftVersion) -> Unit) {
    fun addReference(version: MinecraftVersion, accessorKey: UUID): Unit = transaction {
        JarReferenceTable.insert {
            it[JarReferenceTable.version] = version
            it[JarReferenceTable.accessorKey] = accessorKey
        }
    }
    fun removeReference(version: MinecraftVersion, accessorKey: UUID): Unit = transaction {
        JarReferenceTable.deleteWhere(1) {
            (JarReferenceTable.version eq version) and (JarReferenceTable.accessorKey eq accessorKey)
        }
    }

    init {
        transaction {
            SchemaUtils.create(JarReferenceTable)
        }
    }
}

object JarReferenceTable : Table() {
    val version = jsonb("version", MinecraftVersion.serializer())
    val accessorKey = uuid("accessorKey")

    override val primaryKey = PrimaryKey(version)
}