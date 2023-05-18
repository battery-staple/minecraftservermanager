package com.rohengiralt.minecraftservermanager.domain.model

import java.util.*

data class MinecraftServer(
    val uuid: UUID,
    var name: String,
    val version: MinecraftVersion,
    val runnerUUID: UUID,
)