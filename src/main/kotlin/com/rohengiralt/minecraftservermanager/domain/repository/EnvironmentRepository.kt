package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID

/**
 * A repository that stores [MinecraftServerEnvironment]s
 * @param E a potentially more specific type of environments stored in this repository
 */
interface EnvironmentRepository<E : MinecraftServerEnvironment> {
    /**
     * Gets the environment with uuid [uuid]
     * @return an environment with uuid [uuid], or null if none found
     */
    suspend fun getEnvironment(uuid: EnvironmentUUID): E?

    /**
     * Gets the environment with server uuid [serverUUID]
     * @return an environment with server uuid [serverUUID], or null if none found
     */
    suspend fun getEnvironmentByServer(serverUUID: ServerUUID): E?

    /**
     * Gets a list of all stored environments
     */
    suspend fun getAllEnvironments(): List<E>

    /**
     * Adds [environment] to the repository
     * @return true if [environment] was successfully added; false if it is already present
     */
    suspend fun addEnvironment(environment: E): Boolean

    /**
     * Removes [environment] from the repository, if present
     * @return true if the environment was removed; false if it was not present
     */
    suspend fun removeEnvironment(environment: E): Boolean
}