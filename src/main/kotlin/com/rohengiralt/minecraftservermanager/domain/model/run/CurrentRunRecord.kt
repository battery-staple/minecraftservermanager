package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import kotlinx.datetime.Instant

/**
 * Class that represents a persistently stored record of a current run.
 * May be used to record a current run that existed prior to the runner unexpectedly quitting.
 */
data class MinecraftServerCurrentRunRecord(
    val runUUID: RunUUID,
    val serverUUID: ServerUUID,
    val runnerUUID: RunnerUUID,
    val startTime: Instant
) {
    companion object {
        fun fromCurrentRun(currentRun: MinecraftServerCurrentRun): MinecraftServerCurrentRunRecord =
            MinecraftServerCurrentRunRecord(
                runUUID = currentRun.uuid,
                serverUUID = currentRun.serverUUID,
                runnerUUID = currentRun.runnerUUID,
                startTime = currentRun.startTime
            )
    }
}