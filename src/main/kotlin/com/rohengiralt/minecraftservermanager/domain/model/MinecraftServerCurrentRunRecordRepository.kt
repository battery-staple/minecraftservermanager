package com.rohengiralt.minecraftservermanager.domain.model

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException
import java.time.ZoneOffset
import java.util.*

interface MinecraftServerCurrentRunRecordRepository {
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
            println("Couldn't add current run record, got $e")
            false
        }
    }

    override fun removeRecord(runUUID: UUID): Boolean = transaction {
        try {
            val rowsDeleted = CurrentRunRecordTable.deleteWhere { CurrentRunRecordTable.runUUID eq runUUID }
            rowsDeleted > 0
        } catch (e: SQLException) {
            println("Couldn't add current run record, got $e")
            false
        }
    }

    override fun getAllRecords(): List<MinecraftServerCurrentRunRecord> = transaction {
        CurrentRunRecordTable.selectAll().map {
            with(CurrentRunRecordTable) {
                MinecraftServerCurrentRunRecord(
                    runUUID = it[runUUID],
                    serverUUID = it[serverUUID],
                    runnerUUID = it[runnerUUID],
                    startTime = it[startTimeUtc].toInstant(ZoneOffset.UTC).toKotlinInstant()
                )
            }
        }
    }

    override fun removeAllRecords(): Boolean = transaction {
        val rowsDeleted = CurrentRunRecordTable.deleteAll()
        rowsDeleted > 0
    }
}

private object CurrentRunRecordTable : Table() {
    val runUUID = uuid("run_uuid")
    val serverUUID = uuid("server_uuid")
    val runnerUUID = uuid("runner_uuid")
    val startTimeUtc = datetime("start_time_utc")

    override val primaryKey: PrimaryKey = PrimaryKey(runUUID)
}