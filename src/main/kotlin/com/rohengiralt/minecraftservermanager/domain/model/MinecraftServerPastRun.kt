package com.rohengiralt.minecraftservermanager.domain.model

import kotlinx.datetime.Instant
import java.util.*

data class MinecraftServerPastRun(
    val uuid: UUID,
    val serverId: UUID,
    val runnerId: UUID,
    val startTime: Instant,
    val stopTime: Instant,
    val log: List<LogEntry>,
)

typealias LogEntry = String // Could later include distinctions between input/output, timestamps, etc.