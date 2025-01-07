package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerInitializingRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadOnlyMutexGuardedResource

class InMemoryInitializingRunRepository : InitializingRunRepository {
    private val runsResource = ReadOnlyMutexGuardedResource(mutableMapOf<RunUUID, MinecraftServerInitializingRun>())

    override suspend fun addInitializingRun(run: MinecraftServerInitializingRun): Boolean {
        runsResource.use { runs -> runs[run.uuid] = run }
        return true
    }

    override suspend fun deleteInitializingRun(uuid: RunUUID): MinecraftServerInitializingRun? =
        runsResource.use { runs -> runs.remove(uuid) }

    override suspend fun getAllInitializingRuns(): List<MinecraftServerInitializingRun> =
        runsResource.use { runs -> runs.values.toList() }
}