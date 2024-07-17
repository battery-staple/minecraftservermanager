package com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory

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

// TODO: Remove and integrate into LocalEnvironmentRepository
class LocalMinecraftServerContentDirectoryRepository : KoinComponent { // TODO: Document, remove unused methods
    init {
        transaction {
            SchemaUtils.create(ContentDirectoryTable) //TODO: Concurrency management?
        }
    }

    fun createContentDirectoryIfNotExists(serverUUID: UUID): Boolean =
        getOrCreateContentDirectory(serverUUID) != null

    fun getOrCreateContentDirectory(serverUUID: UUID): Path? =
        getExistingContentDirectory(serverUUID) ?: getAndSaveNewContentDirectory(serverUUID)

    fun getExistingContentDirectory(serverUUID: UUID): Path? = transaction {
        ContentDirectoryTable
            .select { ContentDirectoryTable.serverUUID eq serverUUID }
            .firstOrNull { row ->
                row[ContentDirectoryTable.serverUUID] == serverUUID
            }
            ?.get(ContentDirectoryTable.path)
            ?.let(::Path)
    }

    private fun getAndSaveNewContentDirectory(serverUUID: UUID): Path? =
        contentDirectoryFactory.newContentDirectoryPath(serverUUID)?.let { newPath ->
            saveContentDirectoryToDatabase(
                serverUUID = serverUUID,
                path = newPath
            )
        }

    private fun saveContentDirectoryToDatabase(serverUUID: UUID, path: Path): Path = transaction {
        ContentDirectoryTable
            .insert {
                it[ContentDirectoryTable.serverUUID] = serverUUID
                it[ContentDirectoryTable.path] = path.toRealPath().toString() // TODO: Correct serialization?
            }
        path
    }

    fun deleteContentDirectory(serverUUID: UUID): Boolean {
        val directory = getExistingContentDirectory(serverUUID) ?: run {
            logger.warn("Could not find content directory for server {}", serverUUID)
            return true // TODO: Should this return true or false?
        }

        val databaseDeletionSuccess = deleteContentDirectoryFromDatabase(serverUUID)

        if (!databaseDeletionSuccess) {
            logger.error("Could not delete content directory from database for server {}", serverUUID)
            return false
        }

        val filesystemDeletionSuccess = deleteContentDirectoryFromFilesystem(directory)

        if (!filesystemDeletionSuccess) {
            logger.error("Could not delete content directory from filesystem for server {}", serverUUID)
            return false
        }

        logger.trace("Deleted content directory from database for server {}", serverUUID)

        return true
    }
    private fun deleteContentDirectoryFromDatabase(serverUUID: UUID): Boolean = transaction {
        val rowsDeleted = ContentDirectoryTable.deleteWhere { ContentDirectoryTable.serverUUID eq serverUUID }
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