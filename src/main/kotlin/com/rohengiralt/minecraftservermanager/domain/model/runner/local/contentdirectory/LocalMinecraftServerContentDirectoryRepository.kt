package com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively

class LocalMinecraftServerContentDirectoryRepository : KoinComponent {
    init {
        transaction {
            SchemaUtils.create(ContentDirectoryTable) //TODO: Concurrency management?
        }
    }

    fun createContentDirectoryIfNotExists(server: MinecraftServer): Boolean =
        getOrCreateContentDirectory(server) != null

    fun getOrCreateContentDirectory(server: MinecraftServer): Path? =
        getExistingContentDirectory(server.uuid) ?: getAndSaveNewContentDirectory(server)

    fun getExistingContentDirectory(serverUUID: UUID): Path? = transaction {
        ContentDirectoryTable
            .select { ContentDirectoryTable.serverUUID eq serverUUID }
            .firstOrNull { row ->
                row[ContentDirectoryTable.serverUUID] == serverUUID
            }
            ?.get(ContentDirectoryTable.path)
            ?.let(::Path)
    }

    private fun getAndSaveNewContentDirectory(server: MinecraftServer): Path? =
        contentDirectoryFactory.newContentDirectoryPath(server)?.let { newPath ->
            saveContentDirectoryToDatabase(
                server = server,
                path = newPath
            )
        }

    private fun saveContentDirectoryToDatabase(server: MinecraftServer, path: Path): Path = transaction {
        ContentDirectoryTable
            .insert {
                it[serverUUID] = server.uuid
                it[ContentDirectoryTable.path] = path.toRealPath().toString() // TODO: Correct serialization?
            }
        path
    }

    fun deleteContentDirectory(server: MinecraftServer): Boolean {
        val directory = getExistingContentDirectory(server.uuid) ?: run {
            logger.warn("Could not find content directory for server ${server.uuid}")
            return true // TODO: Should this return true or false?
        }

        val databaseDeletionSuccess = deleteContentDirectoryFromDatabase(server)

        if (!databaseDeletionSuccess) {
            logger.error("Could not delete content directory from database for server ${server.uuid}")
            return false
        }

        val filesystemDeletionSuccess = deleteContentDirectoryFromFilesystem(directory)

        if (!filesystemDeletionSuccess) {
            logger.error("Could not delete content directory from filesystem for server ${server.uuid}")
            return false
        }

        return true
    }
    private fun deleteContentDirectoryFromDatabase(server: MinecraftServer): Boolean = transaction {
        val rowsDeleted = ContentDirectoryTable.deleteWhere { ContentDirectoryTable.serverUUID eq server.uuid }
        rowsDeleted > 0
    }

    @OptIn(ExperimentalPathApi::class)
    private fun deleteContentDirectoryFromFilesystem(directory: Path): Boolean = try {
        directory.deleteIfExists()
    } catch (e: DirectoryNotEmptyException) {
        directory.deleteRecursively()
        true
    } catch (e: IOException) {
        logger.error("Got exception when trying to delete content directory: ${e.message}")
        false
    }

    private val contentDirectoryFactory: LocalMinecraftServerContentDirectoryFactory by inject()
    private val logger = LoggerFactory.getLogger(this::class.java)
}

private object ContentDirectoryTable : Table() {
    val serverUUID: Column<UUID> = uuid("server_uuid")
    val path: Column<String> = text("path")

    override val primaryKey: PrimaryKey = PrimaryKey(serverUUID)
}