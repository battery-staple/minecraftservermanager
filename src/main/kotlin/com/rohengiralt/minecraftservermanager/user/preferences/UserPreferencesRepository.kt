package com.rohengiralt.minecraftservermanager.user.preferences

import com.rohengiralt.minecraftservermanager.user.UserID
import io.ktor.utils.io.errors.*

/**
 * A repository storing each user's preferences.
 */
interface UserPreferencesRepository {
    /**
     * Return the given user's preferences, or null if no preferences found for the given user.
     *
     * @param userId the id of the user whose preferences to retrieve
     */
    fun getUserPreferencesOrNull(userId: UserID): UserPreferences?

    /**
     * Return the given user's preferences. If no preferences have been added previously,
     * attempt to set the user's preferences to [DEFAULT]. If this fails, return null.
     *
     * @param userId the id of the user whose preferences to retrieve
     */
    suspend fun getUserPreferencesOrSetDefaultOrNull(userId: UserID): UserPreferences?

    /**
     * Set the given user's preferences.
     *
     * @param userId the id of the user whose preferences to set
     * @param preferences the user's new preferences
     * @return true if preferences were successfully updated; false otherwise
     */
    suspend fun setUserPreferences(userId: UserID, preferences: UserPreferences): Boolean

    /**
     * Deletes the given user's preferences.
     * @param userId the id of the user whose preferences to delete
     * @return the deleted preferences, or null if there were no preferences to delete
     * @throws IOException if deletion fails
     */
    suspend fun deleteUserPreferences(userId: UserID): UserPreferences?
    companion object {
        /**
         * The default preferences (for new users, for instance).
         */
        val DEFAULT: UserPreferences = UserPreferences(
            serverSortStrategy = UserPreferences.SortStrategy.ALPHABETICAL
        )
    }
}