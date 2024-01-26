package com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards

/**
 * A [MutexGuardedResource] in which [resource] is read-only.
 * @see MutexGuardedResource
 */
data class ReadOnlyMutexGuardedResource<out T>(
    @PublishedApi override val resource: T
) : MutexGuardedResource<T>()