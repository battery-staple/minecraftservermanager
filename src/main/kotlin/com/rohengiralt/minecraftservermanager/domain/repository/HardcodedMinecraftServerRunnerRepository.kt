package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.LocalMinecraftServerRunner

// TODO: Allow mutation; storage of changes
class HardcodedMinecraftServerRunnerRepository : MinecraftServerRunnerRepository {
    private val runners: List<MinecraftServerRunner> = listOf(
        LocalMinecraftServerRunner,
    )

    override fun getRunner(uuid: RunnerUUID): MinecraftServerRunner? =
        runners.find { it.uuid == uuid }

    override fun getRunner(name: String): MinecraftServerRunner? =
        runners.find { it.name == name }

    override fun getAllRunners(): List<MinecraftServerRunner> =
        runners.toList()
}