package com.rohengiralt.minecraftservermanager.util.sql

import io.ktor.utils.io.errors.*
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

/**
 * Executes a transaction in which any uncaught [SQLException]s are converted into [IOException]s.
 * Useful when it's not desirable to propagate a [SQLException].
 */
fun <T> ioExnTransaction(statement: Transaction.() -> T): T =
    try {
        transaction(null, statement)
    } catch (e: SQLException) {
        throw IOException(e)
    }