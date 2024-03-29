package com.rohengiralt.minecraftservermanager.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// https://youtrack.jetbrains.com/issue/KT-15962
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> T?.ifNull(defaultValue: () -> T): T {
    contract {
        callsInPlace(defaultValue, InvocationKind.AT_MOST_ONCE)
    }

    return this ?: defaultValue()
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any> T?.ifNullAlso(block: () -> Unit): T? {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    if (this == null) block()
    return this
}