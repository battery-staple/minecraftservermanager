package com.rohengiralt.minecraftservermanager.domain.model

data class MinecraftServerEnvironment(
    val port: Port? = null,
    val maxHeapSize: MaxHeapSize? = null,
    val minHeapSize: MinHeapSize? = null,
//    val jre: MinecraftServerEnvironmentAspect.JRE? = null
) {
    class Port(val port: com.rohengiralt.minecraftservermanager.domain.model.Port)
    class MaxHeapSize(val memoryMB: UInt)
    class MinHeapSize(val memoryMB: UInt)
//    class JRE(version: JREVersion) TODO

    companion object {
        val EMPTY = MinecraftServerEnvironment(null, null, null)
    }
}