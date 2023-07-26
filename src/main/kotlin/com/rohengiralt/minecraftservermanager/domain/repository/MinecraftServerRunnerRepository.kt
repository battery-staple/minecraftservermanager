package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import java.util.*

interface MinecraftServerRunnerRepository {
    fun getRunner(uuid: UUID): MinecraftServerRunner?
    fun getRunner(name: String): MinecraftServerRunner?

    fun getAllRunners(): List<MinecraftServerRunner>
}