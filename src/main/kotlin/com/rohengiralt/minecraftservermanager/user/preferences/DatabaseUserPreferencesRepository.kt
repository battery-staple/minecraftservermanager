package com.rohengiralt.minecraftservermanager.user.preferences

import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * A UserPreferencesRepository backed by a database.
 */
class DatabaseUserPreferencesRepository : UserPreferencesRepository {
    init {
        transaction {
            SchemaUtils.create(UserPreferencesTable)
        }
    }
    override fun getUserPreferencesOrNull(userId: UserID): UserPreferences? = transaction {
        logger.debug("Trying to get user preferences for user $userId")
        try {
            UserPreferencesTable
                .select { UserPreferencesTable.userId eq userId.idString }
                .singleOrNull()
                ?.toUserPreferences()
        } catch (e: SQLException) {
            logger.error("Getting user preferences for user $userId failed with exception:\n$e")
            null
        }
    }

    override suspend fun getUserPreferencesOrSetDefaultOrNull(userId: UserID): UserPreferences? = mutex.withLock {
        logger.debug("Trying to get user preferences or set default for user $userId")
        val storedPreferences = getUserPreferencesOrNull(userId = userId)
        if (storedPreferences != null) {
            return storedPreferences
        }

        val setSuccess = setUserPreferencesUnsynchronized(userId, UserPreferencesRepository.DEFAULT) // Unsynchronized because we don't want to reenter the mutex
        return if (setSuccess) {
            UserPreferencesRepository.DEFAULT
        } else {
            null
        }
    }

    override suspend fun setUserPreferences(userId: UserID, preferences: UserPreferences): Boolean = mutex.withLock {
        setUserPreferencesUnsynchronized(userId, preferences)
    }

    /**
     * Sets the user preferences.
     * Does not acquire a lock on the mutex, so must be called by a method that locks the mutex for concurrency safety.
     */
    private fun setUserPreferencesUnsynchronized(userId: UserID, preferences: UserPreferences): Boolean = transaction {
        try {
            logger.debug("Trying to set user preferences for user $userId")

            @Suppress("RemoveRedundantQualifierName")
            UserPreferencesTable.upsert(UserPreferencesTable.userId) {
                it[UserPreferencesTable.userId] = userId.idString
                it[UserPreferencesTable.serverSortStrategy] = preferences.serverSortStrategy
            }

            true
        } catch (e: SQLException) {
            logger.error("Setting user preferences for user $userId failed with exception:\n$e")
            false
        }
    }

    override suspend fun deleteUserPreferences(userId: UserID): UserPreferences? = mutex.withLock {
        transaction {
            logger.debug("Trying to delete user preferences for user $userId")

            val deletionCandidates = UserPreferencesTable.select { UserPreferencesTable.userId eq userId.idString }
            val rowsDeleted = UserPreferencesTable.deleteWhere { UserPreferencesTable.userId eq userId.idString }

            if (rowsDeleted >= 1)
                deletionCandidates.single().toUserPreferences()
            else null
        }
    }

    private fun ResultRow.toUserPreferences() = UserPreferences(
        serverSortStrategy = get(UserPreferencesTable.serverSortStrategy),
    )

    private val mutex = Mutex()
    private val logger = LoggerFactory.getLogger(this::class.java)
}

private object UserPreferencesTable : Table() {
    val userId: Column<String> = varchar("user_id", UserID.MAX_LENGTH)
    val serverSortStrategy: Column<UserPreferences.SortStrategy> =
        enumerationByName("server_sort_strategy", UserPreferences.SortStrategy.MAX_NAME_LENGTH, UserPreferences.SortStrategy::class)

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}