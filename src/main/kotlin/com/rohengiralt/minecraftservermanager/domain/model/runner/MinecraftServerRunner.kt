package com.rohengiralt.minecraftservermanager.domain.model.runner

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import kotlinx.coroutines.flow.Flow
import java.util.*

/**
 * Represents a system that can run a Minecraft Server, such as a VM or container.
 */
interface MinecraftServerRunner {
    /**
     * A unique identifier for this runner
     */
    val uuid: UUID

    /**
     * The name of this runner
     */
    var name: String

    /**
     * The domain name from which this runner is reachable
     */
    val domain: String

    /**
     * Prepares all resources required to allow [server] to run.
     * @return true if the environment was successfully set up
     * @throws IllegalArgumentException if the server has already been set up
     */
    suspend fun initializeServer(server: MinecraftServer): Boolean

    /**
     * Deletes or marks for later deletion all resources in use by [server]
     * @return true if the resources were successfully cleaned up
     */
    suspend fun removeServer(server: MinecraftServer): Boolean

    /**
     * Prepares the environment to run the given server, and then runs the server.
     * @param server the server to run
     * @param environmentOverrides additional configuration to specify how the server is run
     * @return a record representing the new run of [server], or null if running failed.
     */
    suspend fun runServer(
        server: MinecraftServer,
        environmentOverrides: MinecraftServerRuntimeEnvironment = MinecraftServerRuntimeEnvironment.EMPTY
    ): MinecraftServerCurrentRun?

    /**
     * Stops the provided run.
     * Note that this method may suspend until the run has been successfully ended.
     * @param uuid the UUID of the run to stop
     * @return true if the run was ended
     * @throws IllegalArgumentException if no run exists with the UUID [uuid].
     */
    suspend fun stopRun(uuid: UUID): Boolean

    /**
     * Stops the provided server's run if one exists.
     * Note that this method may suspend until the run has been successfully ended.
     * @param serverUUID the UUID of the server whose run to stop
     * @return true if the run is stopped.
     *         Returns true even if the server was already stopped before this method.
     */
    suspend fun stopRunByServer(serverUUID: UUID): Boolean

    /**
     * Stops all servers running on this runner.
     * Note that this method may suspend until all runs have been successfully ended.
     * @return true if all runs currently in progress were ended (if nothing was running, returns true)
     */
    suspend fun stopAllRuns(): Boolean

    /**
     * Gets a run with the given [uuid], if such a run exists.
     * @return a run running on this runner with the given [uuid] if it exists, null otherwise.
     */
    suspend fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun?

    /**
     * Gets all current runs on this runner.
     * @return a list of records representing all current runs on this runner. Empty if nothing is running.
     */
    suspend fun getAllCurrentRuns(): List<MinecraftServerCurrentRun>

    /**
     * Gets the current run of the given server on this runner if it exists.
     * @param serverUUID the UUID of the server whose runs to query
     * @return the server's current run if it exists, null otherwise
     */
    suspend fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun?

    /**
     * Queries if the server with the uuid [serverUUID] is running on this runner.
     * @return true if the server exists and is running on this runner, false otherwise.
     */
    suspend fun isRunning(serverUUID: UUID): Boolean = getCurrentRunByServer(serverUUID) != null

    /** TODO: replace this and getCurrentRunByServer with one method returning StateFlow
     * Gets a flow that pushes the new value returned by [getCurrentRunByServer] whenever its value changes.
     * @param server the server whose flow to query
     * @return the update flow
     */
    suspend fun getAllCurrentRunsFlow(server: MinecraftServer): Flow<List<MinecraftServerCurrentRun>>

//    fun addEnvironment(serverRun: E)
//    fun getAllEnvironments(): Sequence<E>
}