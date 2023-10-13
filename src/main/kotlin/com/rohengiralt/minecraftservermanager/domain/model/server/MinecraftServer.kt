package com.rohengiralt.minecraftservermanager.domain.model.server

import kotlinx.datetime.Instant
import java.util.*

data class MinecraftServer(
    val uuid: UUID,
    var name: String,
    val version: MinecraftVersion,
    val runnerUUID: UUID,
    val creationTime: Instant,
)