package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerEnvironmentAspect
import com.rohengiralt.minecraftservermanager.domain.model.Port
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
            port = port?.let { MinecraftServerEnvironmentAspect.Port(Port(it)) },
            maxHeapSize = maxHeapSizeMB?.let { MinecraftServerEnvironmentAspect.MaxHeapSize(it) },
            minHeapSize = minHeapSizeMB?.let { MinecraftServerEnvironmentAspect.MinHeapSize(it) }
        )
}