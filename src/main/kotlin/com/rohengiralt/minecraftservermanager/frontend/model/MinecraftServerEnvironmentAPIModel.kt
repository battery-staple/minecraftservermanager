package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironmentSpec
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerEnvironmentAPIModel( // TODO: value class
    val port: UShort?,
    val maxHeapSizeMB: UInt?,
    val minHeapSizeMB: UInt?
) {
    constructor(environment: MinecraftServerRuntimeEnvironmentSpec) : this(
        port = environment.port?.port?.number,
        maxHeapSizeMB = environment.maxHeapSize?.memoryMB,
        minHeapSizeMB = environment.minHeapSize?.memoryMB
    )

    fun toMinecraftServerEnvironment(): MinecraftServerRuntimeEnvironmentSpec =
        MinecraftServerRuntimeEnvironmentSpec(
            port = port?.let { MinecraftServerRuntimeEnvironmentSpec.Port(Port(it)) },
            maxHeapSize = maxHeapSizeMB?.let { MinecraftServerRuntimeEnvironmentSpec.MaxHeapSize(it) },
            minHeapSize = minHeapSizeMB?.let { MinecraftServerRuntimeEnvironmentSpec.MinHeapSize(it) }
        )
}