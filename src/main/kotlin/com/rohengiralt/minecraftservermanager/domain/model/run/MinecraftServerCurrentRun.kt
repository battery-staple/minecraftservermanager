package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerEnvironment
import com.rohengiralt.shared.serverProcess.ServerIO
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import java.util.*

/**
 * Represents a run of a server currently in process
 */
data class MinecraftServerCurrentRun(
    /**
     * The uuid of the run (not of the server or runner!)
     */
    val uuid: UUID,
    /**
     * The uuid of the server this run belongs to
     */
    val serverUUID: UUID,
    /**
     * The uuid of the runner this run is running on
     */
    val runnerUUID: UUID,
    /**
     * The environment in which this run is running
     */
    val environment: MinecraftServerEnvironment,
    /**
     * The address from which this run can be accessed
     */
    val address: MinecraftServerAddress,
    /**
     * The time the run began execution
     */
    val startTime: Instant,
    /**
     * A channel for inputting commands to the server
     */
    val input: SendChannel<String>,
    /**
     * A flow containing all messages input to and sent by this run
     */
    val interleavedIO: Flow<ServerIO>
)