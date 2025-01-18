package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID

/**
 * Represents a run that is starting but has not fully begun.
 */
data class MinecraftServerInitializingRun(
    override val uuid: RunUUID,
    override val serverUUID: ServerUUID,
    override val runnerUUID: RunnerUUID,
    /**
     * The uuid of the environment in which this run is running
     */
    val environmentUUID: EnvironmentUUID,
) : MinecraftServerRun {}