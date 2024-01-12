package com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Helps enforce that guarded resources are not concurrently accessed by multiple coroutines.
 *
 * Note that while this class makes it easier to prevent race conditions, it **only** guarantees that
 * executions of [use]'s functional parameter are synchronous.
 * Importantly, this class **does not** guarantee that the guarded resources are never accessed concurrently.
 * In particular, it provides no safeguards against callers of [use] leaking the guarded state to other
 * methods or classes with a lifetime longer than that of the [use] scope.
 *
 * Intended usage: declare a class implementing [ResourceContext] that holds the guarded resources,
 * with potentially multiple fields representing any state that must be held consistent.
 * Construct a single instance of this [ResourceContext] and pass it to the constructor of this class.
 *
 * Example:
 * ```
 * // Declaration
 * private class MyResources : ResourceContext {
 *     var property1 = 0
 *     var property2 = 1
 * }
 *
 * private val guardedResources = MutexGuardedResources(MyResources())
 *
 * // Usage
 * guardedResources.use { // Critical Section
 *     property1 = 10 + property2
 * }
 * ```
 *
 * **Warning:** Deadlock *is* possible with this class, in the same way as with a [Mutex].
 * If one coroutine is currently [use]ing an instance of this class and tries to [use] another, a deadlock
 * will occur if there is another coroutine [use]ing the second instance attempting to [use] the first.
 * If two separate instances of this class must be used simultaneously, it is strongly recommended that they
 * each call [use] in the same order.
 *
 * @see [ResourceContext]
 * @see [MutexGuardedResource]
 */
class MutexGuardedResources<RC : ResourceContext>(
    /**
     * The context holding the resource that is to be accessed synchronously.
     */
    @PublishedApi internal val resourceContext: RC
) {
    /**
     * Guards the resources.
     * Invariant: this mutex is held if any [use] block is currently executing.
     */
    @PublishedApi
    internal val mutex: Mutex = Mutex()

    /**
     * Synchronously executes [block], providing it the [ResourceContext] as a context receiver.
     * To ensure concurrency safety, do not store references to any resources in any object with
     * a lifetime potentially greater than [block].
     */
    suspend inline fun <R> use(crossinline block: suspend context(RC) () -> R) =
        mutex.withLock { block(resourceContext) }
}

/**
 * Marker interface for a class that holds several resources, all of which must change consistently.
 * @see MutexGuardedResource
 */
interface ResourceContext {}