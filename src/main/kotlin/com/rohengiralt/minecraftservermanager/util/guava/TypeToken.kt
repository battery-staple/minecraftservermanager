package com.rohengiralt.minecraftservermanager.util.guava

import com.google.common.reflect.TypeToken

/**
 * Creates a [TypeToken] for a particular type
 * @param T the type to create a token for
 */
inline fun <reified T> typeTokenOf(): TypeToken<T> = object : TypeToken<T>() {}