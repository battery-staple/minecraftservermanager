package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.ResourceUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import kotlin.coroutines.CoroutineContext

/**
 * Handles retrieval and caching of [MinecraftServerJar]s.
 */
interface MinecraftServerJarResourceManager { // TODO: Refactor, remove prepareJar
    /**
     * Ensures that a jar with version [version] is present in cache.
     * This method may block, so it should only be called in a [CoroutineContext] equipped to handle this.
     * Once this jar is no longer in use, the caller should also call [freeJar]
     * with the same [accessorKey] as passed to this method to prevent memory/storage leaks.
     * @param version the version of the jar to cache
     * @param accessorKey a key uniquely identifying the user of the jar, used for reference counting.
     * @return true if the jar was successfully cached (or already cached), false if caching failed.
     */
    suspend fun prepareJar(version: MinecraftVersion, accessorKey: ResourceUUID): Boolean

    /**
     * Retrieves a cached jar, or downloads it if not present.
     * This method may block, so it should only be called in a [CoroutineContext] equipped to handle this.
     * Once this jar is no longer in use, the caller should also call [freeJar]
     * with the same [accessorKey] as passed to this method to prevent memory/storage leaks.
     * @param version the version of the jar to cache
     * @param accessorKey a key uniquely identifying the user of the jar, used for reference counting.
     * @return a jar with version [version], or null if retrieval failed.
     */
    suspend fun accessJar(version: MinecraftVersion, accessorKey: ResourceUUID): MinecraftServerJar?

    /**
     * Marks a jar as no longer in use by [accessorKey].
     * Potentially removes the jar from cache if nothing is now using it.
     * @param version the version of the jar to free
     * @param accessorKey a key uniquely identifying the user of the jar, used for reference counting.
     * @return true TODO: remove return value
     */
    suspend fun freeJar(version: MinecraftVersion, accessorKey: ResourceUUID): Boolean
}

suspend fun MinecraftServerJarResourceManager.freeJar(jar: MinecraftServerJar, accessorKey: ResourceUUID) =
    freeJar(jar.version, accessorKey)