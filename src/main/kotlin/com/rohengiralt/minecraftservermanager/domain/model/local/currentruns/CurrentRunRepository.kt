package com.rohengiralt.minecraftservermanager.domain.model.local.currentruns

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerCurrentRun
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

interface CurrentRunRepository {
    fun getCurrentRunByUUID(uuid: UUID): MinecraftServerCurrentRun?
    fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun?
    fun getAllCurrentRuns(): List<MinecraftServerCurrentRun>
    suspend fun addCurrentRun(run: MinecraftServerCurrentRun): Boolean
    suspend fun deleteCurrentRun(uuid: UUID): MinecraftServerCurrentRun?
    suspend fun getCurrentRunsFlow(server: MinecraftServer?): StateFlow<List<MinecraftServerCurrentRun>>
}

class InMemoryCurrentRunRepository : CurrentRunRepository { // TODO: Replace with database backing
    private val mutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.Default) // TODO: Should this be defined here?

    private val currentRunsByUUID = mutableMapOf<UUID, MinecraftServerCurrentRun>()
    private val currentRunsByServerUUID = mutableMapOf<UUID, MinecraftServerCurrentRun>()
    private val allCurrentRuns = MutableStateFlow<List<MinecraftServerCurrentRun>>(emptyList())

    override fun getCurrentRunByUUID(uuid: UUID): MinecraftServerCurrentRun? =
        currentRunsByUUID[uuid]

    override fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun? =
        currentRunsByServerUUID[serverUUID]

    override fun getAllCurrentRuns(): List<MinecraftServerCurrentRun> =
        allCurrentRuns.value

    override suspend fun addCurrentRun(run: MinecraftServerCurrentRun): Boolean = mutex.withLock {
        currentRunsByUUID[run.uuid] = run
        currentRunsByServerUUID[run.serverId] = run
        updateAllCurrentRuns()
        return true
    }

    override suspend fun deleteCurrentRun(uuid: UUID): MinecraftServerCurrentRun? = mutex.withLock {
        currentRunsByUUID.remove(uuid)
            ?.also { run -> currentRunsByServerUUID.remove(run.serverId) }
            ?.also { updateAllCurrentRuns() }
    }

    override suspend fun getCurrentRunsFlow(server: MinecraftServer?): StateFlow<List<MinecraftServerCurrentRun>> =
        if (server == null)
            allCurrentRuns.asStateFlow()
        else
            @OptIn(ExperimentalCoroutinesApi::class)
            allCurrentRuns.mapLatest { runsList -> runsList.filter { it.serverId == server.uuid } }.stateIn(coroutineScope)
    private fun updateAllCurrentRuns() = allCurrentRuns.update { currentRunsByUUID.values.toList() }
}