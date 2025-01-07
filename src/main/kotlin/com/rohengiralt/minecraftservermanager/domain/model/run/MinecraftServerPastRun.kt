package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import kotlinx.datetime.Instant

data class MinecraftServerPastRun(
    override val uuid: RunUUID,
    override val serverUUID: ServerUUID,
    override val runnerUUID: RunnerUUID,
    val startTime: Instant,
    val stopTime: Instant?,
    val log: List<LogEntry>,
) : MinecraftServerRun

typealias LogEntry = String // Could later include distinctions between input/output, timestamps, etc.