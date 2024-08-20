package com.rohengiralt.minecraftservermanager.domain.model.server

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerRuntimeEnvironment( // TODO: Make APIModel only
    val port: Port? = null,
    val maxHeapSize: MaxHeapSize? = null,
    val minHeapSize: MinHeapSize? = null,
//    val jre: JRE? = null
) {
    @Serializable @JvmInline value class Port(val port: com.rohengiralt.minecraftservermanager.domain.model.server.Port)
    @Serializable @JvmInline value class MaxHeapSize(val memoryMB: UInt)
    @Serializable @JvmInline value class MinHeapSize(val memoryMB: UInt)
//    class JRE(version: JREVersion) TODO

    companion object {
        val EMPTY = MinecraftServerRuntimeEnvironment(null, null, null)
    }
}