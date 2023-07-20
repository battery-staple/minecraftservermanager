package com.rohengiralt.minecraftservermanager

import com.rohengiralt.minecraftservermanager.DatabaseSpec.password
import com.rohengiralt.minecraftservermanager.DatabaseSpec.url
import com.rohengiralt.minecraftservermanager.DatabaseSpec.username
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.SQLException

private val databaseConfig = Config { addSpec(DatabaseSpec) }
    .from.env()

private object DatabaseSpec : ConfigSpec() {
    val url by optional("jdbc:postgresql://database:5432/postgres")
    val username by required<String>()
    val password by required<String>()
}

tailrec suspend fun initDatabase(tries: Int = 50) {
    println("Trying to connect to database at ${databaseConfig[url]} with username ${databaseConfig[username]} and password ${databaseConfig[password]}")

    if(!connectToDatabase()) {
        delay(500) // TODO: Exponential backoff
        if (tries > 0) {
            return initDatabase(tries - 1)
        } else {
            error("Could not connect to database")
        }
    }

    println("Connected to database")
}

private fun connectToDatabase(): Boolean {
    try {
        Database.connect(
            databaseConfig[url],
            driver = "org.postgresql.Driver",
            user = databaseConfig[username],
            password = databaseConfig[password]
        )
    } catch (e: SQLException) {
        return false
    }

    return transaction {
        try { // have to catch inside the transaction
            !connection.isClosed
        } catch (e: SQLException) {
            false
        }
    }
}