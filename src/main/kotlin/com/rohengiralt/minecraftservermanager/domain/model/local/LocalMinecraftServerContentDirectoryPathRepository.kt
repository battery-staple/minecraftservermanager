package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path

class LocalMinecraftServerContentDirectoryPathRepository {
    init {
        transaction {
            SchemaUtils.create(ContentDirectoryPathTable) //TODO: Concurrency management?
        }
    }

    fun getContentDirectoryPath(server: MinecraftServer): Path? = transaction {
        ContentDirectoryPathTable
            .select { ContentDirectoryPathTable.serverUUID eq server.uuid }
            .firstOrNull { row ->
                row[ContentDirectoryPathTable.serverUUID] == server.uuid
            }
            ?.get(ContentDirectoryPathTable.path)
            ?.let(::Path)
    }

    fun saveContentDirectoryPath(server: MinecraftServer, path: Path): Path = transaction {
        ContentDirectoryPathTable
            .insert {
                it[this.serverUUID] = server.uuid
                it[this.path] = path.toRealPath().toString() // TODO: Correct serialization?
            }
        path
    }
}

private object ContentDirectoryPathTable : Table() {
    val serverUUID: Column<UUID> = uuid("server_uuid")
    val path: Column<String> = text("path")

    override val primaryKey: PrimaryKey = PrimaryKey(serverUUID)
}