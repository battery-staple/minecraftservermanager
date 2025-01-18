package com.rohengiralt.minecraftservermanager.domain.model.run

import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import kotlinx.datetime.Instant

/**
 * Class that represents a persistently stored record of a current run.
 * May be used to record a current run that existed prior to the runner unexpectedly quitting.
 */
data class MinecraftServerCurrentRunRecord(
    val runUUID: RunUUID,
    val serverUUID: ServerUUID,
    val runnerUUID: RunnerUUID,
    val environmentUUID: EnvironmentUUID,
    val runtimeEnvironment: MinecraftServerRuntimeEnvironment,
    val address: MinecraftServerAddress,
    val startTime: Instant,
    val process: MinecraftServerProcess.Record,
) {
    companion object {
        fun fromCurrentRun(currentRun: MinecraftServerCurrentRun): MinecraftServerCurrentRunRecord =
            MinecraftServerCurrentRunRecord(
                runUUID = currentRun.uuid,
                serverUUID = currentRun.serverUUID,
                runnerUUID = currentRun.runnerUUID,
                environmentUUID = currentRun.environmentUUID,
                runtimeEnvironment = currentRun.runtimeEnvironment,
                address = currentRun.address,
                startTime = currentRun.startTime,
                process = currentRun.process.toRecord(),
            )
    }
}