package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.time.ZoneOffset
import java.util.*

class DatabaseMinecraftServerPastRunRepository : MinecraftServerPastRunRepository {
    init {
        transaction {
            SchemaUtils.create(MinecraftServerPastRunTable)
        }
    }

    override fun getPastRun(uuid: UUID): MinecraftServerPastRun? = transaction {
        MinecraftServerPastRunTable.select {
            MinecraftServerPastRunTable.uuid eq uuid
        }
            .firstOrNull()
            ?.toServerPastRun()
    }

    override fun getAllPastRuns( //TODO: Return with most recent first
        serverUUID: UUID?,
        runnerUUID: UUID?
    ): List<MinecraftServerPastRun> = transaction {
        MinecraftServerPastRunTable.select {
            (serverUUID?.let { matchingServer(it) } ?: Op.TRUE) and
                    (runnerUUID?.let { matchingRun(it) } ?: Op.TRUE)
        }
            .map {
                with(MinecraftServerPastRunTable) {
                    MinecraftServerPastRun(
                        uuid = it[uuid],
                        serverUUID = it[serverId],
                        runnerUUID = it[runnerId],
                        startTime = it[start].toInstant(ZoneOffset.UTC).toKotlinInstant(),
                        stopTime = it[stop]?.toInstant(ZoneOffset.UTC)?.toKotlinInstant(),
                        log = it[log]
                    )
                }
            }
    }

    private fun SqlExpressionBuilder.matchingServer(uuid: UUID) =
        MinecraftServerPastRunTable.serverId eq uuid

    private fun SqlExpressionBuilder.matchingRun(uuid: UUID) =
        MinecraftServerPastRunTable.runnerId eq uuid

    override fun savePastRun(run: MinecraftServerPastRun): Boolean = with(MinecraftServerPastRunTable) {
        logger.debug("Saving past run ${run.uuid}")
        try {
            transaction {
                MinecraftServerPastRunTable.upsert(uuid) { // TODO: Should be insert instead of upsert?
                    it[uuid] = run.uuid
                    it[serverId] = run.serverUUID
                    it[runnerId] = run.runnerUUID
                    it[start] = run.startTime.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
                    it[stop] = run.stopTime?.toLocalDateTime(TimeZone.UTC)?.toJavaLocalDateTime()
                    it[log] = run.log
                }
            }
            logger.info("Successfully saved past run ${run.uuid}")
            true
        } catch (e: SQLException) {
            logger.error("Encountered error saving past run ${run.uuid}: $e")
            false
        } catch (e: DateTimeArithmeticException) {
            logger.error("Encountered error saving past run ${run.uuid}: $e")
            false
        } catch (e: Throwable) {
            logger.error("Encountered error saving past run ${run.uuid}: $e")
            false
        }
    }

    override fun savePastRuns(runs: Iterable<MinecraftServerPastRun>): Boolean =
        runs.all(::savePastRun) // TODO: Database operation instead

    private fun ResultRow.toServerPastRun(): MinecraftServerPastRun = with(MinecraftServerPastRunTable) {
        MinecraftServerPastRun(
            uuid = get(uuid),
            serverUUID = get(serverId),
            runnerUUID = get(runnerId),
            startTime = get(start).toInstant(ZoneOffset.UTC).toKotlinInstant(),
            stopTime = get(stop)?.toInstant(ZoneOffset.UTC)?.toKotlinInstant(),
            log = get(log)
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}

object MinecraftServerPastRunTable : Table() {
    val uuid = uuid("uuid")
    val serverId = uuid("server_uuid").index()
    val runnerId = uuid("runner_uuid").index()
    val start = datetime("start_time_utc")
    val stop = datetime("stop_time_utc").nullable()
    val log = jsonb("log", serializer = ListSerializer(String.serializer())) //TODO: Better data type?

    override val primaryKey = PrimaryKey(uuid)
}