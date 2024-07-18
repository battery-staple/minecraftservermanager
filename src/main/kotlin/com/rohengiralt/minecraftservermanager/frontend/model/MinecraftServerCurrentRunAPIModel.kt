package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerCurrentRunAPIModel(
    val uuid: RunUUID? = null,
    val serverId: ServerUUID? = null,
    val runnerId: RunnerUUID? = null,
    val environment: MinecraftServerEnvironmentAPIModel? = null,
    val address: MinecraftServerAddressAPIModel? = null
) {
    constructor(run: MinecraftServerCurrentRun) : this(
        uuid = run.uuid,
        serverId = run.serverUUID,
        runnerId = run.runnerUUID,
        environment = MinecraftServerEnvironmentAPIModel(run.runtimeEnvironment),
        address = MinecraftServerAddressAPIModel(run.address)
    )
}