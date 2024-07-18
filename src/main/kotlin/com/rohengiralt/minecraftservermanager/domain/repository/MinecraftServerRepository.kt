package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.flow.StateFlow

/**
 * A repository that stores Minecraft servers
 */
interface MinecraftServerRepository {
    /**
     * Gets the server with uuid [uuid].
     * @return the corresponding server, or null if such a server is not present in the repository.
     */
    fun getServer(uuid: ServerUUID): MinecraftServer?

    /**
     * Gets all servers stored in the repository.
     */
    fun getAllServers(): List<MinecraftServer>

    /**
     * Adds the server [minecraftServer] to the repository.
     * Expects that no server with the same `uuid` is already in the repository; if so, does nothing.
     * @return true if the server was successfully added,
     *         false if a server with the same uuid was already in the repository.
     * @throws IOException if adding fails for any reason other than duplicate
     */
    fun addServer(minecraftServer: MinecraftServer): Boolean

    /**
     * Saves the server [minecraftServer] to the repository.
     * If a server with the same `uuid` is already in the repository, replaces it with [minecraftServer].
     * @throws IOException if saving fails
     */
    fun saveServer(minecraftServer: MinecraftServer)

    /**
     * Removes the server with uuid [uuid] from the repository, if such a server exists.
     * @return true if a server was successfully removed; false if no server with uuid [uuid] exists.
     * @throws IOException if deletion fails for any reason other than not being present
     */
    fun removeServer(uuid: ServerUUID): Boolean

    /**
     * Returns a [StateFlow] that always contains the up-to-date state of the server with uuid [uuid].
     * If the server is added or updated, the state flow will emit the new state.
     * If the server is removed, the state flow will emit `null`.
     * If there is an issue with retrieving the server state, the flow will also emit `null`.
     */
    suspend fun getServerUpdates(uuid: ServerUUID): StateFlow<MinecraftServer?>

    /**
     * Returns a [StateFlow] that always contains a list of all servers stored in the repository.
     * If any server is added, updated, or removed, the state flow will emit a new up-to-date list.
     */
    suspend fun getAllUpdates(): StateFlow<List<MinecraftServer>>
}