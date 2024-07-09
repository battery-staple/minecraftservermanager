package com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A version of [MutexGuardedResources] that guards only one resource at a time.
 * Refer to the documentation for [MutexGuardedResources] for details.
 * All warnings pertaining to [MutexGuardedResources] also apply here.
 * 
 * This class is abstract; use [ReadOnlyMutexGuardedResource] or [ReadWriteMutexGuardedResource]
 * depending on if you want this the resource to be readonly or writable, respectively.
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
 *
 * @see MutexGuardedResources
 * @see ReadOnlyMutexGuardedResource
 * @see ReadWriteMutexGuardedResource
 */
abstract class MutexGuardedResource<out T> {
    /**
     * The resource to be accessed synchronously.
     */
    @PublishedApi internal abstract val resource: T

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

    /**
     * Gets a snapshot of the current value.
     */
    suspend inline fun get(): T = use { it }
}

/**
 * Synchronously executes [block], passing it the guarded resource.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T, R> use(resource: MutexGuardedResource<T>, crossinline block: suspend (T) -> R) = resource.use(block)

/**
 * Synchronously executes [block], passing it the resources of all provided [MutexGuardedResource]s.
 * Acquires and releases the locks in the order they are passed to the function.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <reified T, R> useAll(vararg guardedResources: MutexGuardedResource<T>, crossinline block: suspend (Array<out T>) -> R): R {
    // Implementation should mirror Mutex#withLock
    guardedResources.forEach { it.mutex.lock() }
    try {
        return block(guardedResources.map { it.resource }.toTypedArray())
    } finally {
        guardedResources.forEach { it.mutex.unlock() }
    }
}

/**
 * Synchronously executes [block], passing it the resources of all provided [MutexGuardedResource]s.
 * Acquires and releases the locks in the order they are passed to the function.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, R> useAll(
    guardedResource1: MutexGuardedResource<T1>,
    guardedResource2: MutexGuardedResource<T2>,
    crossinline block: suspend (T1, T2) -> R
) = useAll(guardedResource1, guardedResource2) { resources ->
    @Suppress("UNCHECKED_CAST")
    block(
        resources[0] as T1,
        resources[1] as T2,
    )
}

/**
 * Synchronously executes [block], passing it the resources of all provided [MutexGuardedResource]s.
 * Acquires and releases the locks in the order they are passed to the function.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, T3, R> useAll(
    guardedResource1: MutexGuardedResource<T1>,
    guardedResource2: MutexGuardedResource<T2>,
    guardedResource3: MutexGuardedResource<T3>,
    crossinline block: suspend (T1, T2, T3) -> R
) = useAll(guardedResource1, guardedResource2, guardedResource3) { resources ->
    @Suppress("UNCHECKED_CAST")
    block(
        resources[0] as T1,
        resources[1] as T2,
        resources[2] as T3,
    )
}

/**
 * Synchronously executes [block], passing it the resources of all provided [MutexGuardedResource]s.
 * Acquires and releases the locks in the order they are passed to the function.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, T3, T4, R> useAll(
    guardedResource1: MutexGuardedResource<T1>,
    guardedResource2: MutexGuardedResource<T2>,
    guardedResource3: MutexGuardedResource<T3>,
    guardedResource4: MutexGuardedResource<T4>,
    crossinline block: suspend (T1, T2, T3, T4) -> R
) = useAll(guardedResource1, guardedResource2, guardedResource3, guardedResource4) { resources ->
    @Suppress("UNCHECKED_CAST")
    block(
        resources[0] as T1,
        resources[1] as T2,
        resources[2] as T3,
        resources[3] as T4,
    )
}

/**
 * Synchronously executes [block], passing it the resources of all provided [MutexGuardedResource]s.
 * Acquires and releases the locks in the order they are passed to the function.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, T3, T4, T5, R> useAll(
    guardedResource1: MutexGuardedResource<T1>,
    guardedResource2: MutexGuardedResource<T2>,
    guardedResource3: MutexGuardedResource<T3>,
    guardedResource4: MutexGuardedResource<T4>,
    guardedResource5: MutexGuardedResource<T5>,
    crossinline block: suspend (T1, T2, T3, T4, T5) -> R
) = useAll(guardedResource1, guardedResource2, guardedResource3, guardedResource4, guardedResource5) { resources ->
    @Suppress("UNCHECKED_CAST")
    block(
        resources[0] as T1,
        resources[1] as T2,
        resources[2] as T3,
        resources[3] as T4,
        resources[4] as T5,
    )
}