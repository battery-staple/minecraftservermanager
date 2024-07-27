package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.KubernetesEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.MonitorToken
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.insertSuccess
import com.rohengiralt.minecraftservermanager.util.sql.ioExnTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

/**
 * Stores [KubernetesEnvironment]s in the database.
 */
class DatabaseKubernetesEnvironmentRepository : EnvironmentRepository<KubernetesEnvironment> {
    init {
        transaction {
            SchemaUtils.create(KubernetesEnvironmentTable)
        }
    }

    override suspend fun getEnvironment(uuid: EnvironmentUUID): KubernetesEnvironment? = ioExnTransaction {
        KubernetesEnvironmentTable
            .select { KubernetesEnvironmentTable.uuid eq uuid.value }
            .singleOrNull()
            ?.toEnvironment()
    }

    override suspend fun getEnvironmentByServer(serverUUID: ServerUUID): KubernetesEnvironment? = ioExnTransaction {
        KubernetesEnvironmentTable
            .select { KubernetesEnvironmentTable.serverUUID eq serverUUID.value }
            .singleOrNull()
            ?.toEnvironment()
    }

    override suspend fun getAllEnvironments(): List<KubernetesEnvironment> = ioExnTransaction {
        KubernetesEnvironmentTable
            .selectAll()
            .map { it.toEnvironment() }
    }

    override suspend fun addEnvironment(environment: KubernetesEnvironment): Boolean = ioExnTransaction {
        KubernetesEnvironmentTable
            .insertSuccess {
                it[uuid] = environment.uuid.value
                it[serverUUID] = environment.serverUUID.value
                it[runnerUUID] = environment.runnerUUID.value
                it[token] = environment.monitorToken.bytes
            }
    }

    override suspend fun removeEnvironment(environment: KubernetesEnvironment): Boolean = ioExnTransaction {
        val rowsRemoved = KubernetesEnvironmentTable
            .deleteWhere { KubernetesEnvironmentTable.uuid eq environment.uuid.value }

        rowsRemoved > 0
    }

    private fun ResultRow.toEnvironment(): KubernetesEnvironment = KubernetesEnvironment(
        uuid = EnvironmentUUID(this[KubernetesEnvironmentTable.uuid]),
        serverUUID = ServerUUID(this[KubernetesEnvironmentTable.serverUUID]),
        runnerUUID = RunnerUUID(this[KubernetesEnvironmentTable.runnerUUID]),
        monitorToken = MonitorToken(this[KubernetesEnvironmentTable.token])
    )
}

private object KubernetesEnvironmentTable : Table() {
    val uuid: Column<UUID> = uuid("uuid")
    val serverUUID: Column<UUID> = uuid("server_uuid")
    val runnerUUID: Column<UUID> = uuid("runner_uuid")
    val token: Column<ByteArray> = binary("monitor_token")


    override val primaryKey: PrimaryKey = PrimaryKey(uuid)
}