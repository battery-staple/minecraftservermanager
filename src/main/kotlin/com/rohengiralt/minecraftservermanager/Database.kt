package com.rohengiralt.minecraftservermanager

import com.rohengiralt.minecraftservermanager.DatabaseSpec.password
import com.rohengiralt.minecraftservermanager.DatabaseSpec.url
import com.rohengiralt.minecraftservermanager.DatabaseSpec.username
import com.rohengiralt.minecraftservermanager.util.tryWithBackoff
import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

private val databaseConfig = Config { addSpec(DatabaseSpec) }
    .from.env()

private object DatabaseSpec : ConfigSpec() {
    val url by optional("jdbc:postgresql://database:5432/postgres")
    val username by required<String>()
    val password by required<String>()
}

context(CoroutineScope)
suspend fun initDatabase(maxTries: Int) {
    logger.info("Trying to connect to database at ${databaseConfig[url]}")

    tryWithBackoff(
        initialBackoff = DATABASE_INITIAL_BACKOFF,
        onRestart = { _, attempt -> if (attempt == maxTries) error("Could not connect to database") }
    ) { _ -> connectToDatabase() }

    logger.info("Connected to database")
}

private fun connectToDatabase() {
    Database.connect(
        databaseConfig[url],
        driver = "org.postgresql.Driver",
        user = databaseConfig[username],
        password = databaseConfig[password]
    )

    return transaction {
        !connection.isClosed
    }
}

private val logger = LoggerFactory.getLogger("Database")

private val DATABASE_INITIAL_BACKOFF = 10.milliseconds