package com.rohengiralt.minecraftservermanager.domain.model

import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.util.*

class DatabaseMinecraftServerRepository : MinecraftServerRepository {
    init {
        transaction {
            SchemaUtils.create(MinecraftServerTable)
        }
    }

    override fun getServer(uuid: UUID): MinecraftServer? = transaction {
        MinecraftServerTable.select { MinecraftServerTable.uuid eq uuid }
            .singleOrNull()
            ?.toMinecraftServer()
    }

    override fun getAllServers(): List<MinecraftServer> = transaction {
        MinecraftServerTable.selectAll().map { it.toMinecraftServer() }
    }

    override fun addServer(minecraftServer: MinecraftServer): Boolean = transaction {
        succeeds {
            MinecraftServerTable.insert { insertBody(it, minecraftServer) }
        }
    }

    override fun saveServer(minecraftServer: MinecraftServer): Boolean = transaction {
        succeeds {
            MinecraftServerTable.upsert(MinecraftServerTable.uuid) {
                insertBody(it, minecraftServer)
            }
        }
    }

    override fun removeServer(uuid: UUID): Boolean = transaction {
        val rowsDeleted = MinecraftServerTable.deleteWhere { MinecraftServerTable.uuid eq uuid }
        rowsDeleted > 0
    }

    private fun ResultRow.toMinecraftServer() =
        MinecraftServer(
            uuid = get(MinecraftServerTable.uuid),
            name = get(MinecraftServerTable.name),
            version = get(MinecraftServerTable.version),
            runnerUUID = get(MinecraftServerTable.runnerUUID),
        )

    private fun MinecraftServerTable.insertBody(insertStatement: InsertStatement<Number>, server: MinecraftServer) {
        insertStatement[uuid] = server.uuid
        insertStatement[name] = server.name
        insertStatement[version] = server.version
        insertStatement[runnerUUID] = server.runnerUUID
    }

    private inline fun succeeds(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: SQLException) {
            false
        }
}

object MinecraftServerTable : Table() {
    val uuid = uuid("uuid")
    val name = text("name")
    val version = jsonb("version", MinecraftVersion.serializer())
    val runnerUUID = uuid("runner_uuid")

    override val primaryKey = PrimaryKey(uuid)
}