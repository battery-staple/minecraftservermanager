@file:OptIn(ExperimentalContracts::class)

package com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards

import com.rohengiralt.minecraftservermanager.util.concurrency.resourceGuards.ReadWriteMutexGuardedResource.MutableResource
import kotlinx.coroutines.sync.withLock
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A [MutexGuardedResource] in which [resource] is readable and writable.
 * @see MutexGuardedResource
 */
data class ReadWriteMutexGuardedResource<T>(
    @PublishedApi override var resource: T
) : MutexGuardedResource<T>() {
    /**
     * Synchronously executes [block], passing it a [MutableResource] that allows mutation of the underlying resource.
     * If the resource does not need to be mutated in this block, use [use] instead.
     * To ensure concurrency safety, do not store references to any resources in any object with
     * a lifetime potentially greater than [block].
     */
    suspend inline fun <R> useMutable( block: (MutableResource) -> R) =
        mutex.withLock {
            block(MutableResource())
        }

    /**
     * Holds a reference to the mutable resource guarded by this class.
     *
     * Do not attempt to construct this class directly.
     * It should only be used through [useMutable], which ensures the mutex is locked during access.
     */
    inner class MutableResource @PublishedApi internal constructor() {
        var value: T by this@ReadWriteMutexGuardedResource::resource
    }
}

/**
 * Synchronously executes [block], passing it a [MutableResource] that allows mutation of the underlying resource.
 * If the resource does not need to be mutated in this block, use [use] instead.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T, R> useMutable(
    guardedResource: ReadWriteMutexGuardedResource<T>,
    block: (ReadWriteMutexGuardedResource<T>.MutableResource) -> R
) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return guardedResource.mutex.withLock {
        block(guardedResource.MutableResource())
    }
}

/**
 * Synchronously executes [block], passing it a [MutableResource] for all provided [ReadWriteMutexGuardedResource].
 * Acquires and releases the locks in the order they are passed to the function.
 * If the resources do not need to be mutated in this block, use [useAll] instead.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T, R> useAllMutable(
    vararg guardedResources: ReadWriteMutexGuardedResource<T>,
    block: (Array<out ReadWriteMutexGuardedResource<T>.MutableResource>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return useAllMutableUnsafe(*guardedResources) {
        @Suppress("UNCHECKED_CAST")
        block(it as Array<out ReadWriteMutexGuardedResource<T>.MutableResource>)
    }
}

/**
 * Does the same as [useAllMutable], but without type checking any of the resources.
 *
 * Should not be used outside of this file.
 */
@PublishedApi internal suspend inline fun <R> useAllMutableUnsafe(
    vararg guardedResources: ReadWriteMutexGuardedResource<*>,
    block: (Array<out ReadWriteMutexGuardedResource<*>.MutableResource>) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    // Implementation should mirror Mutex#withLock
    guardedResources.forEach { it.mutex.lock() }
    try {
        return block(guardedResources.map { it.MutableResource() }.toTypedArray())
    } finally {
        guardedResources.forEach { it.mutex.unlock() }
    }
}

/**
 * Synchronously executes [block], passing it a [MutableResource] for all provided [ReadWriteMutexGuardedResource].
 * Acquires and releases the locks in the order they are passed to the function.
 * If the resources do not need to be mutated in this block, use [useAll] instead.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, R> useAllMutable(
    guardedResource1: ReadWriteMutexGuardedResource<T1>,
    guardedResource2: ReadWriteMutexGuardedResource<T2>,
    block: (
        ReadWriteMutexGuardedResource<T1>.MutableResource,
        ReadWriteMutexGuardedResource<T2>.MutableResource,
    ) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return useAllMutableUnsafe(
        guardedResource1,
        guardedResource2,
    ) { resources ->
        @Suppress("UNCHECKED_CAST")
        block(
            resources[0] as ReadWriteMutexGuardedResource<T1>.MutableResource,
            resources[1] as ReadWriteMutexGuardedResource<T2>.MutableResource,
        )
    }
}

/**
 * Synchronously executes [block], passing it a [MutableResource] for all provided [ReadWriteMutexGuardedResource].
 * Acquires and releases the locks in the order they are passed to the function.
 * If the resources do not need to be mutated in this block, use [useAll] instead.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, T3, R> useAllMutable(
    guardedResource1: ReadWriteMutexGuardedResource<T1>,
    guardedResource2: ReadWriteMutexGuardedResource<T2>,
    guardedResource3: ReadWriteMutexGuardedResource<T3>,
    block: (
        ReadWriteMutexGuardedResource<T1>.MutableResource,
        ReadWriteMutexGuardedResource<T2>.MutableResource,
        ReadWriteMutexGuardedResource<T3>.MutableResource,
    ) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return useAllMutableUnsafe(
        guardedResource1,
        guardedResource2,
        guardedResource3,
    ) { resources ->
        @Suppress("UNCHECKED_CAST")
        block(
            resources[0] as ReadWriteMutexGuardedResource<T1>.MutableResource,
            resources[1] as ReadWriteMutexGuardedResource<T2>.MutableResource,
            resources[2] as ReadWriteMutexGuardedResource<T3>.MutableResource,
        )
    }
}

/**
 * Synchronously executes [block], passing it a [MutableResource] for all provided [ReadWriteMutexGuardedResource].
 * Acquires and releases the locks in the order they are passed to the function.
 * If the resources do not need to be mutated in this block, use [useAll] instead.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, T3, T4, R> useAllMutable(
    guardedResource1: ReadWriteMutexGuardedResource<T1>,
    guardedResource2: ReadWriteMutexGuardedResource<T2>,
    guardedResource3: ReadWriteMutexGuardedResource<T3>,
    guardedResource4: ReadWriteMutexGuardedResource<T4>,
    block: (
        ReadWriteMutexGuardedResource<T1>.MutableResource,
        ReadWriteMutexGuardedResource<T2>.MutableResource,
        ReadWriteMutexGuardedResource<T3>.MutableResource,
        ReadWriteMutexGuardedResource<T4>.MutableResource,
    ) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return useAllMutableUnsafe(
        guardedResource1,
        guardedResource2,
        guardedResource3,
        guardedResource4,
    ) { resources ->
        @Suppress("UNCHECKED_CAST")
        block(
            resources[0] as ReadWriteMutexGuardedResource<T1>.MutableResource,
            resources[1] as ReadWriteMutexGuardedResource<T2>.MutableResource,
            resources[2] as ReadWriteMutexGuardedResource<T3>.MutableResource,
            resources[3] as ReadWriteMutexGuardedResource<T4>.MutableResource,
        )
    }
}

/**
 * Synchronously executes [block], passing it a [MutableResource] for all provided [ReadWriteMutexGuardedResource].
 * Acquires and releases the locks in the order they are passed to the function.
 * If the resources do not need to be mutated in this block, use [useAll] instead.
 * To ensure concurrency safety, do not store references to any resources in any object with
 * a lifetime potentially greater than [block].
 */
suspend inline fun <T1, T2, T3, T4, T5, R> useAllMutable(
    guardedResource1: ReadWriteMutexGuardedResource<T1>,
    guardedResource2: ReadWriteMutexGuardedResource<T2>,
    guardedResource3: ReadWriteMutexGuardedResource<T3>,
    guardedResource4: ReadWriteMutexGuardedResource<T4>,
    guardedResource5: ReadWriteMutexGuardedResource<T5>,
    block: (
        ReadWriteMutexGuardedResource<T1>.MutableResource,
        ReadWriteMutexGuardedResource<T2>.MutableResource,
        ReadWriteMutexGuardedResource<T3>.MutableResource,
        ReadWriteMutexGuardedResource<T4>.MutableResource,
        ReadWriteMutexGuardedResource<T5>.MutableResource,
    ) -> R
): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return useAllMutableUnsafe(
        guardedResource1,
        guardedResource2,
        guardedResource3,
        guardedResource4,
        guardedResource5,
    ) { resources ->
        @Suppress("UNCHECKED_CAST")
        block(
            resources[0] as ReadWriteMutexGuardedResource<T1>.MutableResource,
            resources[1] as ReadWriteMutexGuardedResource<T2>.MutableResource,
            resources[2] as ReadWriteMutexGuardedResource<T3>.MutableResource,
            resources[3] as ReadWriteMutexGuardedResource<T4>.MutableResource,
            resources[4] as ReadWriteMutexGuardedResource<T5>.MutableResource,
        )
    }
}