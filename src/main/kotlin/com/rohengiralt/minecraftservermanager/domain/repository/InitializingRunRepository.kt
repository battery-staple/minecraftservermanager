package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerInitializingRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID

/**
 * A repository that stores a set of runs that are still initializing and allows lookup by various relevant properties.
 */
interface InitializingRunRepository {
    /**
     * Adds a new initializing run to this repository
     * @return true if the run was successfully added
     */
    suspend fun addInitializingRun(run: MinecraftServerInitializingRun): Boolean

    /**
     * Deletes the initializing run with id [uuid] from this repository, if present
     * @return the deleted run, or null if nothing was deleted
     */
    suspend fun deleteInitializingRun(uuid: RunUUID): MinecraftServerInitializingRun?

    /**
     * All initializing runs currently stored in this repository.
     */
    suspend fun getAllInitializingRuns(): List<MinecraftServerInitializingRun>
}