package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferences
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferencesAPIModel(
    var serverSortStrategy: UserPreferences.SortStrategy? = null // TODO: Custom serializer? Or is default good enough to return to clients
) {
    constructor(userPreferences: UserPreferences) : this(userPreferences.serverSortStrategy)
    fun toUserPreferences(): UserPreferences? {
        return UserPreferences(
            serverSortStrategy = serverSortStrategy ?: return null
        )
    }
}