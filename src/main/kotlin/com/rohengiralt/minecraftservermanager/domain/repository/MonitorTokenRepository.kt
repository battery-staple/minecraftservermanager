package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.MonitorToken
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.insertSuccess
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.logger
import com.rohengiralt.minecraftservermanager.util.sql.suspendIOExnTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.*

/**
 * Holds and creates mappings between servers and their Kubernetes monitor's access tokens.
 */
interface MonitorTokenRepository {
    /**
     * Returns the uuid of the server with token [token], or null if no server has that token.
     */
    suspend fun getServerWithToken(token: MonitorToken): ServerUUID?

    /**
     * Generates and stores a new token for the server with uuid [uuid].
     * @return the new token
     */
    suspend fun generateTokenForServer(uuid: ServerUUID): MonitorToken

    /**
     * Deletes the stored token for the server [uuid]
     * @return true if the token is no longer stored, including if it was not present to begin with.
     */
    suspend fun removeTokenForServer(uuid: ServerUUID): Boolean
}

class DatabaseMonitorTokenRepository : MonitorTokenRepository {
    init {
        transaction {
            SchemaUtils.create(MonitorTokenTable)
        }
    }

    override suspend fun getServerWithToken(token: MonitorToken): ServerUUID? = suspendIOExnTransaction {
        MonitorTokenTable.select { MonitorTokenTable.token eq token.bytes }
            .singleOrNull()
            ?.get(MonitorTokenTable.serverUUID)
            ?.let(::ServerUUID)
    }

    override suspend fun generateTokenForServer(uuid: ServerUUID): MonitorToken = suspendIOExnTransaction {
        val token = generateRandomToken()

        val insertSuccess = MonitorTokenTable.insertSuccess {
            it[MonitorTokenTable.serverUUID] = uuid.value
            it[MonitorTokenTable.token] = token.bytes
        }

        if (!insertSuccess) {
            logger.warn("Token already allocated for server {}", uuid)

            @Suppress("ReplaceGetOrSet") // more consistent this way
            return@suspendIOExnTransaction MonitorTokenTable
                .select { MonitorTokenTable.serverUUID eq uuid.value }
                .single()
                .get(MonitorTokenTable.token)
                .let(::MonitorToken)
        }

        return@suspendIOExnTransaction token
    }

    override suspend fun removeTokenForServer(uuid: ServerUUID): Boolean = suspendIOExnTransaction {
        val rowsDeleted = MonitorTokenTable.deleteWhere { MonitorTokenTable.serverUUID eq uuid.value }

        rowsDeleted > 0
    }

    private suspend fun generateRandomToken(): MonitorToken = withContext(Dispatchers.IO) { // nextBytes may block
        val tokenBytes = ByteArray(128)
        secureRandom.nextBytes(tokenBytes)

        assert(!tokenBytes.all { it == 0.toByte() }) // make sure all bytes were filled
        return@withContext MonitorToken(bytes = tokenBytes)
    }

    private val secureRandom = SecureRandom()
}

object MonitorTokenTable : Table() {
    val serverUUID: Column<UUID> = uuid("server_uuid")
    val token: Column<ByteArray> = binary("monitor_token")

    override val primaryKey: PrimaryKey = PrimaryKey(serverUUID)
}