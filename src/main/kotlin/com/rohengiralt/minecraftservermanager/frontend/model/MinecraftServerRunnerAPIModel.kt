package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerRunnerAPIModel(
    val uuid: RunnerUUID,
    val name: String,
    val domain: String
) {
    constructor(runner: MinecraftServerRunner): this(uuid = runner.uuid, name = runner.name, domain = runner.domain)
}