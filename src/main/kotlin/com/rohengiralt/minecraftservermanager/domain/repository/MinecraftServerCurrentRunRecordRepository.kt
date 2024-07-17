package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.ZoneOffset
import java.util.*

interface MinecraftServerCurrentRunRecordRepository {
    fun getRecord(runUUID: UUID): MinecraftServerCurrentRunRecord?
    fun addRecord(record: MinecraftServerCurrentRunRecord): Boolean
    fun removeRecord(runUUID: UUID): Boolean
    fun getAllRecords(): List<MinecraftServerCurrentRunRecord>
    fun removeAllRecords(): Boolean
}

class DatabaseMinecraftServerCurrentRunRecordRepository : MinecraftServerCurrentRunRecordRepository {
    init {
        transaction {
            SchemaUtils.create(CurrentRunRecordTable)
        }
    }

    override fun getRecord(runUUID: UUID): MinecraftServerCurrentRunRecord? = transaction {
        try {
            CurrentRunRecordTable
                .select { CurrentRunRecordTable.runUUID eq runUUID }
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
                it[runUUID] = record.runUUID
                it[serverUUID] = record.serverUUID
                it[runnerUUID] = record.runnerUUID
                it[startTimeUtc] = record.startTime.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
            }

            true
        } catch (e: SQLException) {
            logger.error("Couldn't add current run record, got $e")
            false
        }
    }

    override fun removeRecord(runUUID: UUID): Boolean = transaction {
        try {
            val rowsDeleted = CurrentRunRecordTable.deleteWhere { CurrentRunRecordTable.runUUID eq runUUID }
            rowsDeleted > 0
        } catch (e: SQLException) {
            logger.error("Couldn't add current run record, got $e")
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
            runUUID = row[runUUID],
            serverUUID = row[serverUUID],
            runnerUUID = row[runnerUUID],
            startTime = row[startTimeUtc].toInstant(ZoneOffset.UTC).toKotlinInstant()
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}

private object CurrentRunRecordTable : Table() {
    val runUUID = uuid("run_uuid")
    val serverUUID = uuid("server_uuid")
    val runnerUUID = uuid("runner_uuid")
    val startTimeUtc = datetime("start_time_utc")

    override val primaryKey: PrimaryKey = PrimaryKey(runUUID)
}