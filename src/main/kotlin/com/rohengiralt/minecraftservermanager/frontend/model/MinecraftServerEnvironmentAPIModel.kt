package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerEnvironmentAPIModel( // TODO: value class
    val port: UShort?,
    val maxHeapSizeMB: UInt?,
    val minHeapSizeMB: UInt?
) {
    constructor(environment: MinecraftServerRuntimeEnvironment) : this(
        port = environment.port?.port?.number,
        maxHeapSizeMB = environment.maxHeapSize?.memoryMB,
        minHeapSizeMB = environment.minHeapSize?.memoryMB
    )

    fun toMinecraftServerEnvironment(): MinecraftServerRuntimeEnvironment =
        MinecraftServerRuntimeEnvironment(
            port = port?.let { MinecraftServerRuntimeEnvironment.Port(Port(it)) },
            maxHeapSize = maxHeapSizeMB?.let { MinecraftServerRuntimeEnvironment.MaxHeapSize(it) },
            minHeapSize = minHeapSizeMB?.let { MinecraftServerRuntimeEnvironment.MinHeapSize(it) }
        )
}