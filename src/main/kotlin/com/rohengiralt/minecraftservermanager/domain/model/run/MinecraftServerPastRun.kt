package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import kotlinx.datetime.Instant

data class MinecraftServerPastRun(
    val uuid: RunUUID,
    val serverUUID: ServerUUID,
    val runnerUUID: RunnerUUID,
    val startTime: Instant,
    val stopTime: Instant?,
    val log: List<LogEntry>,
)

typealias LogEntry = String // Could later include distinctions between input/output, timestamps, etc.