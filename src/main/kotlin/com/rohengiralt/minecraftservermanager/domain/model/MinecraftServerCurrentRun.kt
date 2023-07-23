package com.rohengiralt.minecraftservermanager.domain.model

import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import java.util.*

data class MinecraftServerCurrentRun(
    val uuid: UUID,
    val serverUUID: UUID,
    val runnerUUID: UUID,
    val environment: MinecraftServerEnvironment,
    val address: MinecraftServerAddress,
    val startTime: Instant,
    val input: SendChannel<String>,
    val output: Flow<String>
)