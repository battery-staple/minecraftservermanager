package com.rohengiralt.minecraftservermanager.domain.model

import java.util.*

interface MinecraftServerRunnerRepository {
    fun getRunner(uuid: UUID): MinecraftServerRunner?
    fun getRunner(name: String): MinecraftServerRunner?

    fun getAllRunners(): List<MinecraftServerRunner>
}