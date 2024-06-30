package com.rohengiralt.minecraftservermanager.util.sql

import java.sql.SQLException

val SQLException.state: SQLState
    get() = SQLState.entries.firstOrNull { it.code == sqlState } ?: SQLState.OTHER

enum class SQLState(val code: String?) {
    UNIQUE_VIOLATION("23505"),
    OTHER(null);
}