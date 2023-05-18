package com.rohengiralt.minecraftservermanager.domain.model

import com.rohengiralt.minecraftservermanager.domain.model.local.LocalMinecraftServerRunner
import java.util.*

// TODO: Allow mutation; storage of changes
class HardcodedMinecraftServerRunnerRepository : MinecraftServerRunnerRepository {
    private val runners: List<MinecraftServerRunner> = listOf(
        LocalMinecraftServerRunner,
    )

    override fun getRunner(uuid: UUID): MinecraftServerRunner? =
        runners.find { it.uuid == uuid }

    override fun getRunner(name: String): MinecraftServerRunner? =
        runners.find { it.name == name }

    override fun getAllRunners(): List<MinecraftServerRunner> =
        runners.toList()
}