package com.rohengiralt.minecraftservermanager.util.sql

import java.sql.SQLException

/**
 * A typesafe representation of this exception's [SQLException.SQLState]
 */
val SQLException.state: SQLState
    get() = SQLState.entries.firstOrNull { it.code == sqlState } ?: SQLState.OTHER

/**
 * Recognized SQL state responses.
 * May be Postgres-specific.
 */
enum class SQLState(val code: String?) {
    UNIQUE_VIOLATION("23505"),
    OTHER(null);
}