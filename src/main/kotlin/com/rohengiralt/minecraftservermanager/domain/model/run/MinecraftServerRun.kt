package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID

/**
 * Represents a present, past, or future run of a server.
 */
interface MinecraftServerRun {
    /**
     * The uuid of the run (not of the server or runner!)
     */
    val uuid: RunUUID

    /**
     * The uuid of the server this run belongs to
     */
    val serverUUID: ServerUUID

    /**
     * The uuid of the runner this run is running on
     */
    val runnerUUID: RunnerUUID
}