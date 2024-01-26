package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import kotlinx.coroutines.flow.StateFlow
import java.util.*

/**
 * A repository that stores Minecraft servers
 */
interface MinecraftServerRepository {
    /**
     * Gets the server with uuid [uuid].
     * @return the corresponding server, or null if such a server is not present in the repository.
     */
    fun getServer(uuid: UUID): MinecraftServer?

    /**
     * Gets all servers stored in the repository.
     */
    fun getAllServers(): List<MinecraftServer>

    /**
     * Adds the server [minecraftServer] to the repository.
     * Expects that no server with the same `uuid` is already in the repository; if so, does nothing.
     * @return true if the server was successfully added, false otherwise.
     *              In particular, returns false if a server with the same uuid was already in the repository.
     */
    fun addServer(minecraftServer: MinecraftServer): Boolean

    /**
     * Saves the server [minecraftServer] to the repository.
     * If a server with the same `uuid` is already in the repository, replaces it with [minecraftServer].
     * @return true if the server was successfully saved, including if another server was overwritten; false otherwise.
     */
    fun saveServer(minecraftServer: MinecraftServer): Boolean

    /**
     * Removes the server with uuid [uuid] from the repository, if such a server exists.
     * @return true if a server was successfully removed; false otherwise.
     *              Returns false if no server with uuid [uuid] exists.
     */
    fun removeServer(uuid: UUID): Boolean

    /**
     * Returns a [StateFlow] that always contains the up-to-date state of the server with uuid [uuid].
     * If the server is added or updated, the state flow will emit the new state.
     * If the server is removed, the state flow will emit `null`.
     */
    suspend fun getServerUpdates(uuid: UUID): StateFlow<MinecraftServer?>

    /**
     * Returns a [StateFlow] that always contains a list of all servers stored in the repository.
     * If any server is added, updated, or removed, the state flow will emit a new up-to-date list.
     */
    suspend fun getAllUpdates(): StateFlow<List<MinecraftServer>>
}