package com.rohengiralt.minecraftservermanager.domain.model.local.currentruns

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerCurrentRun
import java.util.*

interface CurrentRunRepository {
    fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun?
    fun getCurrentRuns(server: MinecraftServer?): List<MinecraftServerCurrentRun>
    fun addCurrentRun(run: MinecraftServerCurrentRun): Boolean
    fun deleteCurrentRun(uuid: UUID): MinecraftServerCurrentRun?
}

class InMemoryCurrentRunRepository : CurrentRunRepository { // TODO: Replace with database backing
    private val currentRuns = mutableMapOf<UUID, MinecraftServerCurrentRun>()
    override fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun? =
        currentRuns[uuid]

    override fun getCurrentRuns(server: MinecraftServer?): List<MinecraftServerCurrentRun> =
        if (server == null)
            currentRuns.values.toList()
        else currentRuns.values.filter { it.serverId == server.uuid }

    override fun addCurrentRun(run: MinecraftServerCurrentRun): Boolean {
        currentRuns[run.uuid] = run
        return true
    }

    override fun deleteCurrentRun(uuid: UUID): MinecraftServerCurrentRun? =
        currentRuns.remove(uuid)
}