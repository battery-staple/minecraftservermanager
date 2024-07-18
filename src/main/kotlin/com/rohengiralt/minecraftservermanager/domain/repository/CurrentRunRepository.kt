package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.MutexGuardedResources
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ResourceContext
import com.rohengiralt.minecraftservermanager.util.wrapWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory

/**
 * A repository that stores a set of current runs and allows lookup by various relevant properties.
 */
interface CurrentRunRepository {
    /**
     * Gets the current run with the id [uuid], if present.
     * @return the matching run, or null if not present
     */
    suspend fun getCurrentRunByUUID(uuid: RunUUID): MinecraftServerCurrentRun?

    /**
     * Gets the current run of the server with id [serverUUID], if present.
     * @return the run matching the server, or null if not present
     */
    suspend fun getCurrentRunByServer(serverUUID: ServerUUID): MinecraftServerCurrentRun?

    /**
     * Gets all the current runs in this repository
     * @return a list of all current runs
     */
    suspend fun getAllCurrentRuns(): List<MinecraftServerCurrentRun>

    /**
     * Adds a new current run to this repository
     * @return true if the run was successfully added
     */
    suspend fun addCurrentRun(run: MinecraftServerCurrentRun): Boolean

    /**
     * Deletes the current run with id [uuid] from this repository, if present
     * @return the deleted run, or null if nothing was deleted
     */
    suspend fun deleteCurrentRun(uuid: RunUUID): MinecraftServerCurrentRun?

    /**
     * Gets a [StateFlow] representing all current runs in this repository, optionally filtered by [server]
     * @param server the server whose runs to return. If null, returns all runs in the repository
     * @return a StateFlow representing all filtered current runs,
     * which updates whenever the current runs in this repository do
     */
    suspend fun getCurrentRunsState(server: MinecraftServer?): StateFlow<List<MinecraftServerCurrentRun>>
}

/**
 * A [CurrentRunRepository] that stores current runs in memory.
 * Note that this implies no records will be retained after the application quits or restarts.
 */
class InMemoryCurrentRunRepository : CurrentRunRepository {
    /**
     * The [CoroutineScope] of any coroutines spawned by this class.
     */
    private val coroutineScope = CoroutineScope(Dispatchers.Default) // TODO: Should this be defined here?

    /**
     * Controls access to the various collections of current runs through the [CurrentRunsResourceContext].
     * Since these cannot be updated concurrently, they must only be accessed through the `use` method of 
     * this guard.
     */
    private val currentRunsGuard = MutexGuardedResources(CurrentRunsResourceContext())

    /**
     * A context encapsulating all fields holding current runs that must be updated together.
     */
    private class CurrentRunsResourceContext : ResourceContext {
        /**
         * Contains all [MinecraftServerCurrentRun]s stored in the repository.
         */
        val allCurrentRuns = MutableStateFlow<List<MinecraftServerCurrentRun>>(emptyList())

        /**
         * A map of run UUIDs to the runs themselves.
         *
         * Contains all [MinecraftServerCurrentRun]s stored in the repository.
         */
        val currentRunsByRunUUID = mutableMapOf<RunUUID, MinecraftServerCurrentRun>()

        /**
         * A map of server UUIDs to their current runs.
         *
         * Contains all [MinecraftServerCurrentRun]s stored in the repository.
         */
        val currentRunsByServerUUID = mutableMapOf<ServerUUID, MinecraftServerCurrentRun>()
    }

    /**
     * Asserts the class invariant is satisfied.
     */
    context(CurrentRunsResourceContext)
    private fun assertInv() {
        val currAllCurrentRuns = allCurrentRuns.value.toSet()
        assert(currAllCurrentRuns == currentRunsByRunUUID.values.toSet()) {
            """currentRunsByRunUUID does not match the current value of allCurrentRuns
               currentRunsByRunUUID: $currentRunsByRunUUID
               allCurrentRuns: $currAllCurrentRuns""".trimIndent()
        }
        assert(currAllCurrentRuns == currentRunsByServerUUID.values.toSet()) {
            """currentRunsByServerUUID does not match the current value of allCurrentRuns
               currentRunsByServerUUID: $currentRunsByServerUUID
               allCurrentRuns: $currAllCurrentRuns""".trimIndent()
        }
    }

    override suspend fun getCurrentRunByUUID(uuid: RunUUID): MinecraftServerCurrentRun? = currentRunsGuard.use {
        logger.debug("Getting current run {} by UUID", uuid)
        currentRunsByRunUUID[uuid]
    }

    override suspend fun getCurrentRunByServer(serverUUID: ServerUUID): MinecraftServerCurrentRun? = currentRunsGuard.use {
        logger.debug("Getting current run for server {}", serverUUID)
        currentRunsByServerUUID[serverUUID]
    }

    override suspend fun getAllCurrentRuns(): List<MinecraftServerCurrentRun> = currentRunsGuard.use {
        logger.debug("Getting all current runs")
        allCurrentRuns.value
    }

    override suspend fun addCurrentRun(run: MinecraftServerCurrentRun): Boolean = currentRunsGuard.use {
        logger.debug("Adding current run {}", run.uuid)
        wrapWith({ assertInv() }) {
            currentRunsByRunUUID[run.uuid] = run
            currentRunsByServerUUID[run.serverUUID] = run
            updateAllCurrentRuns()
        }
        logger.debug("Added current run")
        true
    }

    override suspend fun deleteCurrentRun(uuid: RunUUID): MinecraftServerCurrentRun? = currentRunsGuard.use {
        logger.debug("Deleting current run {}", uuid)
        wrapWith({ assertInv() }) {
            currentRunsByRunUUID.remove(uuid)
                ?.also { run ->
                    currentRunsByServerUUID.remove(run.serverUUID)
                        .also { assert(it != null) }
                }
                .also { updateAllCurrentRuns() }
        }.also { logger.debug("Deleted current run") }
    }

    override suspend fun getCurrentRunsState(server: MinecraftServer?): StateFlow<List<MinecraftServerCurrentRun>> = currentRunsGuard.use {
        logger.debug("Getting current runs state for server {}", server?.uuid)
        wrapWith({ assertInv() }) {
            if (server == null)
                allCurrentRuns.asStateFlow()
            else
                @OptIn(ExperimentalCoroutinesApi::class)
                allCurrentRuns.mapLatest { latestRunList -> latestRunList.filter { it.serverUUID == server.uuid } }
                    .stateIn(coroutineScope)
        }
    }

    context(CurrentRunsResourceContext)
    private fun updateAllCurrentRuns() = allCurrentRuns.update { currentRunsByRunUUID.values.toList() }

    private val logger = LoggerFactory.getLogger(this::class.java)
}