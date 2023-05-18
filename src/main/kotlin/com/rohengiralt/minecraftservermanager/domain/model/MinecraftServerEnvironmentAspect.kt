package com.rohengiralt.minecraftservermanager.domain.model

sealed class MinecraftServerEnvironmentAspect { // TODO: Rename, "Aspect" sucks and is misleading
    class Port(val port: com.rohengiralt.minecraftservermanager.domain.model.Port) : MinecraftServerEnvironmentAspect()
    class MaxHeapSize(val memoryMB: UInt) : MinecraftServerEnvironmentAspect()
    class MinHeapSize(val memoryMB: UInt) : MinecraftServerEnvironmentAspect()
//    class JRE(version: JREVersion) : MinecraftServerEnvironmentAspect() TODO
}

data class MinecraftServerEnvironment(
    val port: MinecraftServerEnvironmentAspect.Port? = null,
    val maxHeapSize: MinecraftServerEnvironmentAspect.MaxHeapSize? = null,
    val minHeapSize: MinecraftServerEnvironmentAspect.MinHeapSize? = null,
//    val jre: MinecraftServerEnvironmentAspect.JRE? = null
) {
    companion object {
        val EMPTY = MinecraftServerEnvironment(null, null, null,)
    }
}