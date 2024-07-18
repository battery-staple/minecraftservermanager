package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.ResourceUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import com.rohengiralt.shared.serverProcess.ServerIO
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.*

/**
 * Represents a run of a server currently in process
 */
data class MinecraftServerCurrentRun(
    /**
     * The uuid of the run (not of the server or runner!)
     */
    val uuid: RunUUID,
    /**
     * The uuid of the server this run belongs to
     */
    val serverUUID: ServerUUID,
    /**
     * The uuid of the runner this run is running on
     */
    val runnerUUID: RunnerUUID,
    /**
     * The uuid of the environment in which this run is running
     */
    val environmentUUID: EnvironmentUUID,
    /**
     * The specification for the runtime environment in which this run is running
     */
    val runtimeEnvironment: MinecraftServerRuntimeEnvironment, // TODO: Replace with something else
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

@Serializable
@JvmInline
value class RunUUID(
    @Serializable(with = UUIDSerializer::class) override val value: UUID
) : ResourceUUID