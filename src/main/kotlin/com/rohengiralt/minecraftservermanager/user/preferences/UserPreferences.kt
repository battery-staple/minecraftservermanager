package com.rohengiralt.minecraftservermanager.user.preferences

import kotlinx.serialization.Serializable

/**
 * The user's current set of preferences.
 */
data class UserPreferences(
    val serverSortStrategy: SortStrategy,
) {
    /**
     * An enum representing possible ways to sort minecraft servers on the main page
     * All names guaranteed to be <= 255 characters long.
     */
    @Serializable
    enum class SortStrategy {
        NEWEST, OLDEST, ALPHABETICAL;

        companion object {
            const val MAX_NAME_LENGTH = 255 // For use in database
            init {
                assert(entries.toTypedArray().all { it.name.length <= 255 })
            }
        }
    }
}