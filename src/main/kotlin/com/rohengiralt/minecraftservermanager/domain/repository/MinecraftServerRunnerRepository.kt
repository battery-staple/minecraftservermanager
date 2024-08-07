package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import io.ktor.utils.io.errors.*

interface MinecraftServerRunnerRepository {
    /**
     * Gets a runner by its UUID.
     * @return a runner with UUID [uuid], or null if no runner exists with that UUID
     */
    fun getRunner(uuid: RunnerUUID): MinecraftServerRunner?

    /**
     * Gets a runner by its name.
     * @return a runner with name [name], or null if no runner exists with that name
     */
    fun getRunner(name: String): MinecraftServerRunner?

    /**
     * Gets all the runners stored in this repository
     * @throws IOException if retrieval fails
     */
    fun getAllRunners(): List<MinecraftServerRunner>
}