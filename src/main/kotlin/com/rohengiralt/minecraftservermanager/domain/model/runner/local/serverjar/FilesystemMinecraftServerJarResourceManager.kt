package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.ResourceUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.server.versionType
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.ifNullAlso
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.sql.SQLException
import kotlin.io.path.*

class FilesystemMinecraftServerJarResourceManager(private val directoryName: String) : MinecraftServerJarResourceManager, KoinComponent {
    override suspend fun prepareJar(version: MinecraftVersion, accessorKey: ResourceUUID): Boolean { // TODO: Delete cached jar after some time?
        logger.debug("Preparing jar with version {}", version)
        jarReferenceCounter.addReference(version, accessorKey)
        return if (getCachedJar(version) != null) {
            true
        } else {
            cacheNewJar(version) != null
        }
    }

    override suspend fun accessJar(version: MinecraftVersion, accessorKey: ResourceUUID): MinecraftServerJar? {
        logger.debug("Trying to access with jar {} and accessor {}", version, accessorKey)
        if (!prepareJar(version, accessorKey)) return null

        return getCachedJar(version)
    }

    override suspend fun freeJar(version: MinecraftVersion, accessorKey: ResourceUUID): Boolean {
        logger.debug("Freeing jar with version {} and accessor {}", version, accessorKey)
        jarReferenceCounter.removeReference(version, accessorKey)
        return true
    }

    private fun getCachedJar(version: MinecraftVersion): MinecraftServerJar? {
        logger.debug("Trying to get cached jar for version ${version.versionString}")
        return directory.useDirectoryEntries { entries ->
            entries
                .map {
                    it to JarFileName
                        .fromString(it.nameWithoutExtension)
                        .ifNullAlso {
                            // TODO: Add scanning (periodic? on startup?) to ensure directory has no strangely named jars
                            logger.warn("Could not parse jar file name of ${it.nameWithoutExtension}")
                        }
                }
                .firstOrNull { (_, name) ->
                    version == name?.version
                }
        }?.let { (path, name) ->
            if (name == null) {
                return null
            }
            MinecraftServerJar(path, version)
        }.ifNullAlso { logger.trace("Could not find cached jar") }
    }

    /**
     * Prevents concurrent attempts to cache a new jar.
     * This is necessary because caching a new jar requires both creating a jar and moving it.
     * These two steps must be done atomically; if not, we might overwrite a jar in the middle of moving it.
     */
    private val cacheNewJarMutex = Mutex()

    private suspend fun cacheNewJar(version: MinecraftVersion): MinecraftServerJar? = cacheNewJarMutex.withLock {
        logger.debug("Trying to cache new jar for version {}", version)
        return jarFactory.newJar(version)?.let(::cacheJar)
    }

    private fun cacheJar(jar: MinecraftServerJar): MinecraftServerJar? {
        logger.debug("Caching new jar with version ${jar.version.versionString}")
        return try {
            val name = JarFileName(jar.version)

            jar.copy(
                path = jar.path.moveTo(directory / "$name.jar", overwrite = true)
            )
        } catch (e: IOException) {
            logger.error("Got exception when trying to save jar: ${e.message}")
            null
        }
    }

    private fun deleteJar(version: MinecraftVersion): Boolean {
        logger.debug("Deleting jar for version ${version.versionString}")
        return try {
            directory.useDirectoryEntries { entries ->
                entries.filter { entryPath ->
                    entryPath.nameWithoutExtension == JarFileName(version).toString()
                }.toList().also {
                    logger.trace("Found ${it.size} deletion candidates")
                    if (it.isEmpty()) {
                        logger.trace("Found no jars to delete")
                        return true // TODO: Should return true or false here?
                    }
                }.all {
                    it.deleteIfExists() // Not transactional; TODO: is that bad?
                }
            }
        } catch (e: IOException) {
            logger.error("Got exception when trying to delete jar: ${e.message}")
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

    private val logger = LoggerFactory.getLogger(this::class.java)
}

private class JarReferenceCounter(private val deleteJar: (version: MinecraftVersion) -> Unit) {
    fun addReference(version: MinecraftVersion, accessorKey: ResourceUUID) {
        logger.debug("Adding reference to jar with version {} and accessor {}", version, accessorKey)
        transaction {
            JarReferenceTable.insertIgnore {
                it[JarReferenceTable.version] = version
                it[JarReferenceTable.accessorKey] = accessorKey.value
            }
        }
    }
    fun removeReference(version: MinecraftVersion, accessorKey: ResourceUUID) {
        logger.debug("Removing reference to jar with version {} and accessor {}", version, accessorKey)
        transaction {
            try {
                JarReferenceTable.deleteWhere {
                    (JarReferenceTable.version eq version) and (JarReferenceTable.accessorKey eq accessorKey.value)
                }
            } catch (e: SQLException) {
                // TODO: Should it just swallow the error like this if there are no current references?
                logger.error("Couldn't delete jar reference from database, got $e")
            }

            val currentReferences = JarReferenceTable
                .select { (JarReferenceTable.version eq version) }
                .count()

            logger.trace("After removing reference, $currentReferences remain")

            if (currentReferences <= 0) {
                logger.trace("No references left; deleting jar with version {}", version.versionString)
                deleteJar(version)
            }
        }
    }

    init {
        transaction {
            SchemaUtils.create(JarReferenceTable)
        }
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}

object JarReferenceTable : Table() {
    val version = jsonb("version", MinecraftVersion.serializer())
    val accessorKey = uuid("accessorKey")

    override val primaryKey = PrimaryKey(version, accessorKey)
}