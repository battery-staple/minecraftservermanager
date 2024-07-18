package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerPastRunAPIModel(
    val uuid: RunUUID,
    val serverId: ServerUUID,
    val runnerId: RunnerUUID,
    @Serializable val startTime: Instant,
    @Serializable val stopTime: Instant?,
    @Serializable val log: List<String>,
) {
    constructor(pastRun: MinecraftServerPastRun) : this(
        uuid = pastRun.uuid,
        serverId = pastRun.serverUUID,
        runnerId = pastRun.runnerUUID,
        startTime = pastRun.startTime,
        stopTime = pastRun.stopTime,
        log = pastRun.log
    )
}