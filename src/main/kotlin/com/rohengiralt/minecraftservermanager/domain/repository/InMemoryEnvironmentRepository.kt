package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadOnlyMutexGuardedResource
import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.useAll
import com.rohengiralt.minecraftservermanager.util.wrapWith
import java.util.*

/**
 * An [EnvironmentRepository] that stores environments exclusively in memory.
 * This means that all data in this repository is lost on application restarts,
 * so it should never be used for data that is meant to be persistent.
 */
class InMemoryEnvironmentRepository : EnvironmentRepository {
    /*
     * Class invariant: lock acquisition MUST occur in the following order to prevent deadlock:
     * 1. byEnvCacheResource
     * 2. byServerCacheResource
     */

    private val byEnvCacheResource: ReadOnlyMutexGuardedResource<MutableMap<UUID, MinecraftServerEnvironment>> =
        ReadOnlyMutexGuardedResource(mutableMapOf())

    private val byServerCacheResource: ReadOnlyMutexGuardedResource<MutableMap<UUID, MinecraftServerEnvironment>> =
        ReadOnlyMutexGuardedResource(mutableMapOf())

    override suspend fun getEnvironment(environmentUUID: UUID): MinecraftServerEnvironment? = wrapWith({ assertInv() }) {
        byEnvCacheResource.use { cache -> cache[environmentUUID] }
    }

    override suspend fun getEnvironmentByServer(serverUUID: UUID): MinecraftServerEnvironment? = wrapWith({ assertInv() }) {
        byServerCacheResource.use { cache -> cache[serverUUID] }
    }

    override suspend fun getAllEnvironments(): List<MinecraftServerEnvironment> =
        byEnvCacheResource.use { cache -> cache.values.toList() }

    override suspend fun addEnvironment(environment: MinecraftServerEnvironment): Boolean = wrapWith({ assertInv() }) {
        useAll(byEnvCacheResource, byServerCacheResource) { byEnvCache, byServerCache ->
            byEnvCache[environment.uuid] = environment
            byServerCache[environment.serverUUID] = environment
        }

        return true
    }

    override suspend fun removeEnvironment(environment: MinecraftServerEnvironment): Boolean = wrapWith({ assertInv() }) {
        useAll(byEnvCacheResource, byServerCacheResource) { byEnvCache, byServerCache ->
            byEnvCache.remove(environment.uuid) ?: return false
            byServerCache.remove(environment.serverUUID).also { assert(it != null) }
        }

        return true
    }

    private suspend fun assertInv() {
        useAll(byEnvCacheResource, byServerCacheResource) { byEnvCache, byServerCache ->
            assert(byEnvCache.values.toSet() == byServerCache.values.toSet())
        }
    }
}