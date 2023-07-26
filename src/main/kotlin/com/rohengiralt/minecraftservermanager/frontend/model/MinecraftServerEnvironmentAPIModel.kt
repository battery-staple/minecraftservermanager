package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerEnvironmentAPIModel( // TODO: value class
    val port: UShort?,
    val maxHeapSizeMB: UInt?,
    val minHeapSizeMB: UInt?
) {
    constructor(environment: MinecraftServerEnvironment) : this(
        port = environment.port?.port?.number,
        maxHeapSizeMB = environment.maxHeapSize?.memoryMB,
        minHeapSizeMB = environment.minHeapSize?.memoryMB
    )

    fun toMinecraftServerEnvironment(): MinecraftServerEnvironment =
        MinecraftServerEnvironment(
            port = port?.let { MinecraftServerEnvironment.Port(Port(it)) },
            maxHeapSize = maxHeapSizeMB?.let { MinecraftServerEnvironment.MaxHeapSize(it) },
            minHeapSize = minHeapSizeMB?.let { MinecraftServerEnvironment.MinHeapSize(it) }
        )
}