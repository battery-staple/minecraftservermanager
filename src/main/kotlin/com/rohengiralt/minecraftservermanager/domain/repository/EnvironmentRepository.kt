package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import java.util.*

/**
 * A repository that stores [MinecraftServerEnvironment]s
 */
interface EnvironmentRepository {
    /**
     * Gets the environment with uuid [environmentUUID]
     * @return an environment with uuid [environmentUUID], or null if none found
     */
    suspend fun getEnvironment(environmentUUID: UUID): MinecraftServerEnvironment?

    /**
     * Gets the environment with server uuid [serverUUID]
     * @return an environment with server uuid [serverUUID], or null if none found
     */
    suspend fun getEnvironmentByServer(serverUUID: UUID): MinecraftServerEnvironment?

    /**
     * Gets a list of all stored environments
     */
    suspend fun getAllEnvironments(): List<MinecraftServerEnvironment>

    /**
     * Adds [environment] to the repository
     * @return true if [environment] was successfully added; false if it is already present
     */
    suspend fun addEnvironment(environment: MinecraftServerEnvironment): Boolean

    /**
     * Removes [environment] from the repository, if present
     * @return true if the environment was removed; false if it was not present
     */
    suspend fun removeEnvironment(environment: MinecraftServerEnvironment): Boolean
}