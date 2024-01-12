package com.rohengiralt.minecraftservermanager.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Calls [wrapper], then [block], then [wrapper] again, returning the result of [block].
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> wrapWith(wrapper: () -> Unit, block: () -> T): T {
    contract {
        callsInPlace(wrapper, InvocationKind.AT_LEAST_ONCE)
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    wrapper()
    val returnValue = block()
    wrapper()
    return returnValue
}