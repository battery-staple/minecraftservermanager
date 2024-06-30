package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadOnlyMutexGuardedResource
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadWriteMutexGuardedResource
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.useAll
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
import com.rohengiralt.minecraftservermanager.util.ifTrue.ifTrueAlso
import com.rohengiralt.minecraftservermanager.util.sql.SQLState
import com.rohengiralt.minecraftservermanager.util.sql.state
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.ZoneOffset
import java.util.*

/**
 * A [MinecraftServerRepository] that persists servers in a database
 */
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
        try {
            MinecraftServerTable.insert { insertBody(it, minecraftServer) }
        } catch (e: SQLException) {
            if (e.state == SQLState.UNIQUE_VIOLATION) {
                return@transaction false
            } else throw e
        }
        return@transaction true
    }.ifTrueAlso { serverWatcher.pushUpdate(minecraftServer) }

    override fun saveServer(minecraftServer: MinecraftServer) {
        transaction {
            MinecraftServerTable.upsert(MinecraftServerTable.uuid) {
                insertBody(it, minecraftServer)
            }
        }

        serverWatcher.pushUpdate(minecraftServer)
    }

    override fun removeServer(uuid: UUID): Boolean = transaction {
        val rowsDeleted = MinecraftServerTable.deleteWhere { MinecraftServerTable.uuid eq uuid }
        rowsDeleted > 0
    }.ifTrueAlso { serverWatcher.pushDelete(uuid) }

    override suspend fun getServerUpdates(uuid: UUID): StateFlow<MinecraftServer?> {
        val initialServer = try {
            getServer(uuid)
        } catch (e: SQLException) {
            logger.warn("Failed to get server for updates flow with UUID $uuid")
            null
        }

        return serverWatcher.serverUpdatesFlow(uuid, initialServer)
    }

    override suspend fun getAllUpdates(): StateFlow<List<MinecraftServer>> =
        serverWatcher.allUpdatesFlow(getAllServers())

    private fun ResultRow.toMinecraftServer() =
        MinecraftServer(
            uuid = get(MinecraftServerTable.uuid),
            name = get(MinecraftServerTable.name),
            version = get(MinecraftServerTable.version),
            runnerUUID = get(MinecraftServerTable.runnerUUID),
            creationTime = get(MinecraftServerTable.creationTime).toInstant(ZoneOffset.UTC).toKotlinInstant()
        )

    private fun MinecraftServerTable.insertBody(insertStatement: InsertStatement<Number>, server: MinecraftServer) {
        insertStatement[uuid] = server.uuid
        insertStatement[name] = server.name
        insertStatement[version] = server.version
        insertStatement[runnerUUID] = server.runnerUUID
        insertStatement[creationTime] = server.creationTime.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}

private class ServerWatcher {
    // Class Invariant: Must always acquire resources in the following order:
    // 1. watchingServerFlowsResource
    // 2. allUpdatesFlowResource

    /**
     * A resource guarding all the state flows that emit when a particular server is updated (including deleted).
     */
    private val watchingServerFlowsResource = ReadOnlyMutexGuardedResource(mutableMapOf<UUID, MutableStateFlow<MinecraftServer?>>())

    /**
     * A resource guarding a state flow that emits when any server is updated (including deleted).
     * The state flow is null until [allUpdatesFlow] is first called.
     */
    private val allUpdatesFlowResource = ReadWriteMutexGuardedResource<MutableStateFlow<List<MinecraftServer>>?>(null)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    suspend fun serverUpdatesFlow(uuid: UUID, initialValue: MinecraftServer?): StateFlow<MinecraftServer?> = watchingServerFlowsResource.use { watchingServerFlows ->
        watchingServerFlows.getOrPut(uuid) {
            MutableStateFlow(initialValue)
        }.asStateFlow()
    }

    /**
     * Returns a state flow that always contains an up-to-date list of all the servers in the database,
     * emitting whenever one is created, changed, or deleted.
     *
     * @param initialValue the initial value of the state flow. Only used the first time this method is called.
     */
    suspend fun allUpdatesFlow(initialValue: List<MinecraftServer>): StateFlow<List<MinecraftServer>> = allUpdatesFlowResource.useMutable { allUpdatesFlow ->
        var allUpdatesFlowValue = allUpdatesFlow.value // Necessary because the compiler doesn't know
                                                       // that allUpdatesFlow.value is only changed within this method.
        if (allUpdatesFlowValue == null) {
            assert(allUpdatesFlow.value == null) { "All updates flow somehow changed while mutex was held" }
            allUpdatesFlowValue = MutableStateFlow(initialValue)
            allUpdatesFlow.value = allUpdatesFlowValue
        }

        return@useMutable allUpdatesFlowValue
    }

    /**
     * Updates all flows dependent on this server's updates, causing them to emit [server].
     * This method should be called whenever the server is updated in the database, including when first created.
     */
    fun pushUpdate(server: MinecraftServer) = coroutineScope.launch { // TODO: Some sort of error if emitting takes too long?
        useAll(watchingServerFlowsResource, allUpdatesFlowResource) { watchingServerFlows, allUpdatesFlow ->
            watchingServerFlows[server.uuid]?.emit(server)
            allUpdatesFlow?.update { servers -> servers + server }
        }
    }

    /**
     * Updates all flows dependent on this server's updates, causing them to signal deletion.
     * This method should be called whenever the server with uuid [uuid] is deleted.
     */
    fun pushDelete(uuid: UUID) = coroutineScope.launch {
        useAll(watchingServerFlowsResource, allUpdatesFlowResource) { watchingServerFlows, allUpdatesFlow ->
            watchingServerFlows[uuid]?.emit(null)
            allUpdatesFlow?.update { servers ->
                servers.filterNot { server -> server.uuid == uuid }
            }
        }
    }
}

object MinecraftServerTable : Table() {
    val uuid = uuid("uuid")
    val name = text("name")
    val version = jsonb("version", MinecraftVersion.serializer())
    val runnerUUID = uuid("runner_uuid")
    val creationTime = datetime("creation_time_utc")

    override val primaryKey = PrimaryKey(uuid)
}