package com.rohengiralt.minecraftservermanager.util.extensions.exposed

import com.rohengiralt.minecraftservermanager.util.sql.SQLState
import com.rohengiralt.minecraftservermanager.util.sql.state
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.sql.SQLException

/**
 * Performs an insert operation and returns whether the operation succeeded.
 * @return true if an row was inserted; false if a conflicting row already existed
 * @throws SQLException if any error other than conflict occurs
 */
fun <T : Table> T.insertSuccess(body: T.(InsertStatement<Number>) -> Unit): Boolean {
    try {
        insert(body)
    } catch (e: SQLException) {
        if (e.state == SQLState.UNIQUE_VIOLATION) {
            return false
        } else throw e
    }
    return true
}