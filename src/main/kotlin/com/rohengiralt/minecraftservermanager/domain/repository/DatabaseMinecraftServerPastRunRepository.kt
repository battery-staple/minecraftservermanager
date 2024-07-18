package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerPastRunTable.uuid
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
import com.rohengiralt.minecraftservermanager.util.sql.ioExnTransaction
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.ZoneOffset

class DatabaseMinecraftServerPastRunRepository : MinecraftServerPastRunRepository {
    init {
        transaction {
            SchemaUtils.create(MinecraftServerPastRunTable)
        }
    }

    override fun getPastRun(uuid: RunUUID): MinecraftServerPastRun? = ioExnTransaction {
        MinecraftServerPastRunTable.select {
            MinecraftServerPastRunTable.uuid eq uuid.value
        }
            .firstOrNull()
            ?.toServerPastRun()
    }

    override fun getAllPastRuns( //TODO: Return with most recent first
        serverUUID: ServerUUID?,
        runnerUUID: RunnerUUID?
    ): List<MinecraftServerPastRun> = ioExnTransaction {
        MinecraftServerPastRunTable
            .select { serverFilter(serverUUID) and runnerFilter(runnerUUID) }
            .map { it.toServerPastRun() }
    }

    /**
     * Returns a SQL operator that is true if a server has UUID [serverUUID],
     * or, if [serverUUID] is null, a filter that matches all servers.
     */
    private fun serverFilter(serverUUID: ServerUUID?): Op<Boolean> {
        return if (serverUUID != null) {
            MinecraftServerPastRunTable.serverId eq serverUUID.value
        } else Op.TRUE // No filter
    }

    /**
     * Returns a SQL operator that is true if a server has UUID [runnerUUID],
     * or, if [runnerUUID] is null, a filter that matches all servers.
     */
    private fun runnerFilter(runnerUUID: RunnerUUID?) = if (runnerUUID != null) {
        MinecraftServerPastRunTable.runnerId eq runnerUUID.value
    } else Op.TRUE // No filter

    override fun savePastRun(run: MinecraftServerPastRun) {
        logger.debug("Saving past run {}", run.uuid)
        ioExnTransaction {
            MinecraftServerPastRunTable.upsert(uuid) { // TODO: Should be insert instead of upsert?
                it[uuid] = run.uuid.value
                it[serverId] = run.serverUUID.value
                it[runnerId] = run.runnerUUID.value
                it[start] = run.startTime.toLocalDateTime(TimeZone.UTC).toJavaLocalDateTime()
                it[stop] = run.stopTime?.toLocalDateTime(TimeZone.UTC)?.toJavaLocalDateTime()
                it[log] = run.log
            }
        }
        logger.info("Successfully saved past run ${run.uuid}")
    }

    override fun savePastRuns(runs: Iterable<MinecraftServerPastRun>) =
        runs.forEach(::savePastRun) // TODO: Database operation instead

    private fun ResultRow.toServerPastRun(): MinecraftServerPastRun = with(MinecraftServerPastRunTable) {
        MinecraftServerPastRun(
            uuid = RunUUID(get(uuid)),
            serverUUID = ServerUUID(get(serverId)),
            runnerUUID = RunnerUUID(get(runnerId)),
            startTime = get(start).toInstant(ZoneOffset.UTC).toKotlinInstant(),
            stopTime = get(stop)?.toInstant(ZoneOffset.UTC)?.toKotlinInstant(),
            log = get(log)
        )
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
}

private object MinecraftServerPastRunTable : Table() {
    val uuid = uuid("uuid")
    val serverId = uuid("server_uuid").index()
    val runnerId = uuid("runner_uuid").index()
    val start = datetime("start_time_utc")
    val stop = datetime("stop_time_utc").nullable()
    val log = jsonb("log", serializer = ListSerializer(String.serializer())) //TODO: Better data type?

    override val primaryKey = PrimaryKey(uuid)
}