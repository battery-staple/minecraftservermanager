package com.rohengiralt.minecraftservermanager.domain.model.server

import com.rohengiralt.minecraftservermanager.domain.ResourceUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.*

data class MinecraftServer(
    val uuid: ServerUUID,
    var name: String,
    val version: MinecraftVersion,
    val runnerUUID: RunnerUUID,
    val creationTime: Instant,
)

@Serializable
@JvmInline
value class ServerUUID(
    @Serializable(with = UUIDSerializer::class)
    override val value: UUID
) : ResourceUUID