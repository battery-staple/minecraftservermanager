package com.rohengiralt.minecraftservermanager.domain.model.run

import kotlinx.datetime.Instant
import java.util.*

/**
 * Class that represents a persistently stored record of a current run.
 * May be used to record a current run that existed prior to the runner unexpectedly quitting.
 */
data class MinecraftServerCurrentRunRecord(
    val runUUID: UUID,
    val serverUUID: UUID,
    val runnerUUID: UUID,
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