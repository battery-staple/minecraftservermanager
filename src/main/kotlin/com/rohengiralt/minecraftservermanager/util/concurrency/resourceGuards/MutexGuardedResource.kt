package com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A version of [MutexGuardedResources] that guards only one (mutable, but constant) resource at a time.
 * Refer to the documentation for [MutexGuardedResources] for details.
 * All warnings pertaining to [MutexGuardedResources] also apply here.
 *
 * Example:
 * ```
 * // Declaration
 * private val guardedResource = MutexGuardedResource(myMutableObject)
 *
 * // Usage
 * guardedResource.use { // Critical Section
 *     it.value += 10
 * }
 * ```
 */
class MutexGuardedResource<T>(
    /**
     * The resource to be accessed synchronously.
     */
    @PublishedApi internal val resource: T
) {
    /**
     * Guards the resources.
     * Invariant: this mutex is held if any [use] block is currently executing.
     */
    @PublishedApi internal val mutex: Mutex = Mutex()

    /**
     * Synchronously executes [block], passing it the guarded resource.
     * To ensure concurrency safety, do not store references to any resources in any object with
     * a lifetime potentially greater than [block].
     */
    suspend inline fun <R> use(crossinline block: suspend (T) -> R) =
         mutex.withLock { block(resource) }
}