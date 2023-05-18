package com.rohengiralt.minecraftservermanager.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// https://youtrack.jetbrains.com/issue/KT-15962
@OptIn(ExperimentalContracts::class)
public inline fun <T : Any> T?.ifNull(defaultValue: () -> T): T {
    contract {
        callsInPlace(defaultValue, InvocationKind.AT_MOST_ONCE)
    }

    return this ?: defaultValue()
}