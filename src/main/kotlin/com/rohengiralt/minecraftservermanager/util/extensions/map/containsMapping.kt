package com.rohengiralt.minecraftservermanager.util.extensions.map

/**
 * Returns true if [mapping]`.first` is mapped to [mapping]`.second` in this.
 */
fun <T, U> Map<T, U>.containsMapping(mapping: Pair<T, U>): Boolean =
    get(mapping.first) == mapping.second

/**
 * An implementation of the `contains` operator that delegates to [containsMapping]
 * @see containsMapping
 */
operator fun <T, U> Map<T, U>.contains(mapping: Pair<T, U>): Boolean =
    containsMapping(mapping)