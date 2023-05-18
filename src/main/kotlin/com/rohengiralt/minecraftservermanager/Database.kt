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

internal val config = Config { addSpec(DatabaseSpec) }
    .from.env()

internal object DatabaseSpec : ConfigSpec() {
    val url by optional("jdbc:postgresql://database:5432/postgres")
    val username by required<String>()
    val password by required<String>()
}

tailrec suspend fun initDatabase(tries: Int = 50) {
    println("Trying to connect to database at ${config[url]} with username ${config[username]} and password ${config[password]}")

    if(!connectToDatabase()) {
        delay(500)
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
            config[url],
            driver = "org.postgresql.Driver",
            user = config[username],
            password = config[password]
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