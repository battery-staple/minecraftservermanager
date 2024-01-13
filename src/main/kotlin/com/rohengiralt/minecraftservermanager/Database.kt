package com.rohengiralt.minecraftservermanager

import com.rohengiralt.minecraftservermanager.DatabaseSpec.password
import com.rohengiralt.minecraftservermanager.DatabaseSpec.url
import com.rohengiralt.minecraftservermanager.DatabaseSpec.username
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.sql.SQLException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val databaseConfig = Config { addSpec(DatabaseSpec) }
    .from.env()

private object DatabaseSpec : ConfigSpec() {
    val url by optional("jdbc:postgresql://database:5432/postgres")
    val username by required<String>()
    val password by required<String>()
}

tailrec suspend fun initDatabase(maxTries: Int, delayTime: Duration = 10.milliseconds) {
    logger.info("Trying to connect to database at ${databaseConfig[url]}")

    if (connectToDatabase()) {
        logger.info("Connected to database")
    } else {
        delay(delayTime)
        if (maxTries > 0) {
            logger.warn("Could not connect to database. Retrying in ${delayTime * 2}.")
            return initDatabase(maxTries = maxTries - 1, delayTime = delayTime * 2)
        } else {
            logger.error("Could not connect to database.")
            error("Could not connect to database")
        }
    }
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
        try { // have to catch inside the transaction to prevent logging for some reason
            !connection.isClosed
        } catch (e: SQLException) {
            false
        }
    }
}

private val logger = LoggerFactory.getLogger("Database")