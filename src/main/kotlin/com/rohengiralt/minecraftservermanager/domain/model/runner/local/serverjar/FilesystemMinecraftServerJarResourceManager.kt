package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.server.versionType
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.ifNullAlso
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.IOException
import java.sql.SQLException
import java.util.*
import kotlin.io.path.*

class FilesystemMinecraftServerJarResourceManager(private val directoryName: String) : MinecraftServerJarResourceManager, KoinComponent {
    override suspend fun prepareJar(version: MinecraftVersion, accessorKey: UUID): Boolean { // TODO: Delete after some time?
        println("Preparing jar with version $version")
        jarReferenceCounter.addReference(version, accessorKey)
        return if (getCachedJar(version) != null) {
            true
        } else {
            cacheNewJar(version) != null
        }
    }

    override suspend fun accessJar(version: MinecraftVersion, accessorKey: UUID): MinecraftServerJar? {
        println("Trying to access with jar $version and accessor $accessorKey")
        if (!prepareJar(version, accessorKey)) return null

        return getCachedJar(version)
    }

    override suspend fun freeJar(version: MinecraftVersion, accessorKey: UUID): Boolean {
        println("Freeing jar with version $version and accessor $accessorKey")
        jarReferenceCounter.removeReference(version, accessorKey)
        return true
    }

    private fun getCachedJar(version: MinecraftVersion): MinecraftServerJar? {
        println("Trying to get cached jar for version ${version.versionString}")
        return directory.useDirectoryEntries { entries ->
            entries
                .map {
                    it to JarFileName
                        .fromString(it.nameWithoutExtension)
                        .ifNullAlso {
                            // TODO: Add scanning (periodic? on startup?) to ensure directory has no strangely named jars
                            println("Could not parse jar file name of ${it.nameWithoutExtension}")
                        }
                }
                .firstOrNull { (_, name) ->
                    version == name?.version
                }
        }?.let { (path, name) ->
            if (name == null) {
                println("Could not find cached jar")
                return null
            }
            MinecraftServerJar(path, version)
        }
    }

    private suspend fun cacheNewJar(version: MinecraftVersion): MinecraftServerJar? {
        println("Trying to cache new jar for version $version")
        return jarFactory.newJar(version).let(::cacheJar)
    }

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
                    entryPath.nameWithoutExtension == JarFileName(version).toString()
                }.toList().also {
                    println("Found ${it.size} deletion candidates")
                    if (it.isEmpty()) {
                        println("Found no jars to delete")
                        return true // TODO: Should return true or false here?
                    }
                }.all {
                    it.deleteIfExists() // Not transactional; TODO: is that bad?
                }
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
                val versionType = try {
                    MinecraftVersion.VersionType.valueOf(versionTypeString)
                } catch (e: IllegalArgumentException) {
                    return null
                }

                return JarFileName(
                    MinecraftVersion.fromString(
                        versionString,
                        versionType
                    ) ?: return null
                )
            }

        }
    }
}

private class JarReferenceCounter(private val deleteJar: (version: MinecraftVersion) -> Unit) {
    fun addReference(version: MinecraftVersion, accessorKey: UUID) {
        println("Adding reference to jar with version $version and accessor $accessorKey")
        transaction {
            JarReferenceTable.insertIgnore {
                it[JarReferenceTable.version] = version
                it[JarReferenceTable.accessorKey] = accessorKey
            }
        }
    }
    fun removeReference(version: MinecraftVersion, accessorKey: UUID) {
        println("Removing reference to jar with version $version and accessor $accessorKey")
        transaction {
            try {
                JarReferenceTable.deleteWhere {
                    (JarReferenceTable.version eq version) and (JarReferenceTable.accessorKey eq accessorKey)
                }
            } catch (e: SQLException) {
                // TODO: Should it just swallow the error like this if there are no current references?
                println("Couldn't delete jar reference from database, got $e")
            }

            val currentReferences = JarReferenceTable
                .select { (JarReferenceTable.version eq version) }
                .count()

            println("After removing reference, $currentReferences remain")

            if (currentReferences <= 0) {
                deleteJar(version)
            }
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

    override val primaryKey = PrimaryKey(version, accessorKey)
}