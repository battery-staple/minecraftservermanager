package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MinecraftServerRunnerAPIModel(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
    val name: String,
    val domain: String
) {
    constructor(runner: MinecraftServerRunner): this(uuid = runner.uuid, name = runner.name, domain = runner.domain)
}