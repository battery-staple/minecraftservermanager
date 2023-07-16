package com.rohengiralt.minecraftservermanager.domain.model

import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
import com.rohengiralt.minecraftservermanager.util.ifTrue.ifTrueAlso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    private val serverWatcher = ServerWatcher()

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
    }.ifTrueAlso { serverWatcher.pushUpdate(minecraftServer) }

    override fun saveServer(minecraftServer: MinecraftServer): Boolean = transaction {
        succeeds {
            MinecraftServerTable.upsert(MinecraftServerTable.uuid) {
                insertBody(it, minecraftServer)
            }
        }
    }.ifTrueAlso { serverWatcher.pushUpdate(minecraftServer) }

    override fun removeServer(uuid: UUID): Boolean = transaction {
        val rowsDeleted = MinecraftServerTable.deleteWhere { MinecraftServerTable.uuid eq uuid }
        rowsDeleted > 0
    }.ifTrueAlso { serverWatcher.pushDelete(uuid) }

    override fun getServerUpdates(uuid: UUID): Flow<MinecraftServer?> =
        serverWatcher.updatesFlow(uuid, getServer(uuid))

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

private class ServerWatcher {
    private val watchingServerChannels = mutableMapOf<UUID, MutableStateFlow<MinecraftServer?>>()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun updatesFlow(uuid: UUID, initialValue: MinecraftServer?): StateFlow<MinecraftServer?> =
        watchingServerChannels.getOrPut(uuid) {
            MutableStateFlow(initialValue)
        }.asStateFlow()

    fun pushUpdate(server: MinecraftServer) = coroutineScope.launch { // TODO: Some sort of error if emitting takes too long?
        watchingServerChannels[server.uuid]?.emit(server)
    }

    fun pushDelete(uuid: UUID) = coroutineScope.launch {
        watchingServerChannels[uuid]?.emit(null)
    }
}


object MinecraftServerTable : Table() {
    val uuid = uuid("uuid")
    val name = text("name")
    val version = jsonb("version", MinecraftVersion.serializer())
    val runnerUUID = uuid("runner_uuid")

    override val primaryKey = PrimaryKey(uuid)
}