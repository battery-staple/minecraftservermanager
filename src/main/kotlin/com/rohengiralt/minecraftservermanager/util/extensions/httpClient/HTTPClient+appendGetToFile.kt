package com.rohengiralt.minecraftservermanager.util.extensions.httpClient

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.appendBytes

suspend inline fun HttpClient.appendGetToPath(urlString: String, path: Path): Boolean {
    logger.debug("Appending contents from url ({}) to path ({})", urlString, path)
    val httpResponse = get(urlString)
    if (!httpResponse.status.isSuccess()) {
        logger.warn("Failed to get file, got code ${httpResponse.status}")
        return false
    }

    val channel: ByteReadChannel = httpResponse.body()

    logger.trace("Writing to file")
    while (!channel.isClosedForRead) {
        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())

        withContext(Dispatchers.IO) {
            while (packet.isNotEmpty) {
                val bytes = packet.readBytes()
                path.appendBytes(bytes)
            }
        }
    }
    logger.trace("Successfully appended content to path")

    return true
}

@PublishedApi
internal val logger = LoggerFactory.getLogger("appendGetToFile")