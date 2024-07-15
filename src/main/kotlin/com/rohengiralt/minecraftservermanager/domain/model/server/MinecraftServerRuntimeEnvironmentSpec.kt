package com.rohengiralt.minecraftservermanager.domain.model.server

data class MinecraftServerRuntimeEnvironmentSpec( // TODO: Make APIModel only
    val port: Port? = null,
    val maxHeapSize: MaxHeapSize? = null,
    val minHeapSize: MinHeapSize? = null,
//    val jre: JRE? = null
) {
    class Port(val port: com.rohengiralt.minecraftservermanager.domain.model.server.Port)
    class MaxHeapSize(val memoryMB: UInt)
    class MinHeapSize(val memoryMB: UInt)
//    class JRE(version: JREVersion) TODO

    companion object {
        val EMPTY = MinecraftServerRuntimeEnvironmentSpec(null, null, null)
    }
}