package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.KubernetesRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.LocalMinecraftServerRunner
import java.util.*

// TODO: Allow mutation; storage of changes
class HardcodedMinecraftServerRunnerRepository : MinecraftServerRunnerRepository {
    private val runners: List<MinecraftServerRunner> = listOf(
        LocalMinecraftServerRunner,
        KubernetesRunner(RunnerUUID(UUID.fromString("16e477ad-4cf4-4413-9d17-f246d372211e")) /* Randomly generated, but constant */),
    )

    override fun getRunner(uuid: RunnerUUID): MinecraftServerRunner? =
        runners.find { it.uuid == uuid }

    override fun getRunner(name: String): MinecraftServerRunner? =
        runners.find { it.name == name }

    override fun getAllRunners(): List<MinecraftServerRunner> =
        runners.toList()
}