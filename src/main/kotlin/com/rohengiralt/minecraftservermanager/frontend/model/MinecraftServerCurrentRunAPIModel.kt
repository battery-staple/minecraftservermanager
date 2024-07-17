package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MinecraftServerCurrentRunAPIModel(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val serverId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val runnerId: UUID? = null,
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