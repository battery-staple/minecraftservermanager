package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MinecraftServerPastRunAPIModel(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    @Serializable(with = UUIDSerializer::class) val serverId: UUID,
    @Serializable(with = UUIDSerializer::class) val runnerId: UUID,
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