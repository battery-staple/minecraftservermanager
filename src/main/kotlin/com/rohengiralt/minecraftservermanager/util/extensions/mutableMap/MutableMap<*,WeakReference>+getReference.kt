package com.rohengiralt.minecraftservermanager.util.extensions.mutableMap

import java.lang.ref.WeakReference

fun <K, V: Any> MutableMap<K, WeakReference<V>>.getReference(key: K): V? =
    get(key)?.let { ref ->
        ref.get().also { value ->
            if (value == null) remove(key)
        }
    }