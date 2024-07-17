package com.rohengiralt.minecraftservermanager.domain.model.runner

import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * An environment in which a particular Minecraft Server can be run
 */
interface MinecraftServerEnvironment {
    val uuid: UUID

    /**
     * The server that can be run in this environment
     */
    val serverUUID: UUID

    /**
     * The runner that provisioned this environment
     */
    val runnerUUID: UUID

    /**
     * Runs the server associated with this environment.
     * @param port the port on which to expose the running server
     * @param maxHeapSizeMB the maximum heap size the server is allowed to use
     * @param minHeapSizeMB the minimum heap size the server is allowed to use
     * @return a representation of the new run, or null if the server failed to run
     */
    suspend fun runServer(
        port: Port,
        maxHeapSizeMB: UInt,
        minHeapSizeMB: UInt
    ): MinecraftServerProcess?

    /**
     * The server process that is currently running
     */
    val currentProcess : StateFlow<MinecraftServerProcess?>
}