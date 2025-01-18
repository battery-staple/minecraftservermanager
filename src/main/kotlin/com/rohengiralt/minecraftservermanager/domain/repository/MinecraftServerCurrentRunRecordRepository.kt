package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.ZoneOffset

interface MinecraftServerCurrentRunRecordRepository {
    fun getRecord(runUUID: RunUUID): MinecraftServerCurrentRunRecord?
    fun addRecord(record: MinecraftServerCurrentRunRecord): Boolean
    fun removeRecord(runUUID: RunUUID): Boolean
    fun getAllRecords(): List<MinecraftServerCurrentRunRecord>
    fun removeAllRecords(): Boolean
}

class DatabaseMinecraftServerCurrentRunRecordRepository : MinecraftServerCurrentRunRecordRepository {
    init {
        transaction {
            SchemaUtils.create(CurrentRunRecordTable)
        }
    }

    override fun getRecord(runUUID: RunUUID): MinecraftServerCurrentRunRecord? = transaction {
        try {
            CurrentRunRecordTable
                .select { CurrentRunRecordTable.runUUID eq runUUID.value }
                .singleOrNull()
                ?.toRecord()
        } catch (e: SQLException) {
            logger.error("Couldn't get current run record, got $e")
            null
        }
    }

    override fun addRecord(record: MinecraftServerCurrentRunRecord): Boolean = transaction {
        try {
            CurrentRunRecordTable.insertIgnore {
                it[runUUID] = record.runUUID.value
                it[serverUUID] = record.serverUUID.value
                it[runnerUUID] = record.runnerUUID.value
                it[environmentUUID] = record.environmentUUID.value
                it[port] = record.runtimeEnvironment.port?.port?.number
                it[maxHeapSize] = record.runtimeEnvironment.maxHeapSize?.memoryMB
                it[minHeapSize] = record.runtimeEnvironment.minHeapSize?.memoryMB
                it[address] = record.address
                it[startTimeUtc] = record.startTime.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
                it[process] = record.process
            }

            true
        } catch (e: SQLException) {
            logger.error("Couldn't add current run record, got $e")
            false
        }
    }

    override fun removeRecord(runUUID: RunUUID): Boolean = transaction {
        try {
            val rowsDeleted = CurrentRunRecordTable.deleteWhere { CurrentRunRecordTable.runUUID eq runUUID.value }
            rowsDeleted > 0
        } catch (e: SQLException) {
            logger.error("Couldn't remove current run record, got $e")
            false
        }
    }

    override fun getAllRecords(): List<MinecraftServerCurrentRunRecord> = transaction {
        CurrentRunRecordTable.selectAll().map { it.toRecord() }
    }

    override fun removeAllRecords(): Boolean = transaction {
        val rowsDeleted = CurrentRunRecordTable.deleteAll()
        rowsDeleted > 0
    }

    private fun ResultRow.toRecord(): MinecraftServerCurrentRunRecord = with(CurrentRunRecordTable) {
        val row = this@toRecord
        MinecraftServerCurrentRunRecord(
            runUUID = RunUUID(row[runUUID]),
            serverUUID = ServerUUID(row[serverUUID]),
            runnerUUID = RunnerUUID(row[runnerUUID]),
            environmentUUID = EnvironmentUUID(row[environmentUUID]),
            runtimeEnvironment = MinecraftServerRuntimeEnvironment(
                port = row[port]?.let { portNum -> MinecraftServerRuntimeEnvironment.Port(Port(portNum)) },
                maxHeapSize = row[maxHeapSize]?.let { size -> MinecraftServerRuntimeEnvironment.MaxHeapSize(size) },
                minHeapSize = row[minHeapSize]?.let { size -> MinecraftServerRuntimeEnvironment.MinHeapSize(size) },
            ),
            address = row[address],
            startTime = row[startTimeUtc].toInstant(ZoneOffset.UTC).toKotlinInstant(),
            process = row[process],
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}

@OptIn(ExperimentalUnsignedTypes::class)
private object CurrentRunRecordTable : Table() {
    val runUUID = uuid("run_uuid")
    val serverUUID = uuid("server_uuid")
    val runnerUUID = uuid("runner_uuid")
    val environmentUUID = uuid("environment_uuid")
    val port = ushort("port").nullable()
    val maxHeapSize = uinteger("max_heap_size").nullable()
    val minHeapSize = uinteger("min_heap_size").nullable()
    val address = jsonb("address", MinecraftServerAddress.serializer())
    val startTimeUtc = datetime("start_time_utc")
    val process = jsonb("process", serializer<MinecraftServerProcess.Record>())

    override val primaryKey: PrimaryKey = PrimaryKey(runUUID)
}