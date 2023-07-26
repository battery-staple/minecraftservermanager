package com.rohengiralt.minecraftservermanager.domain.model.run

import kotlinx.datetime.Instant
import java.util.*

data class MinecraftServerPastRun(
    val uuid: UUID,
    val serverUUID: UUID,
    val runnerUUID: UUID,
    val startTime: Instant,
    val stopTime: Instant?,
    val log: List<LogEntry>,
)

typealias LogEntry = String // Could later include distinctions between input/output, timestamps, etc.