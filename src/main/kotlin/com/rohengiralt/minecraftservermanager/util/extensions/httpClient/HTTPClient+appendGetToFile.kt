package com.rohengiralt.minecraftservermanager.util.extensions.httpClient

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.appendBytes

/**
 * Makes a GET request to [url] and appends the returned contents to [Path].
 * @return true if the contents were successfully appended, false if not.
 */
suspend inline fun HttpClient.appendGetToPath(url: String, path: Path): Boolean {
    logger.debug("Appending contents from url ({}) to path ({})", url, path)
    val httpResponse = try {
        get(url)
    } catch (e: IOException) {
        logger.warn("Failed to get file", e)
        return false
    }

    if (!httpResponse.status.isSuccess()) {
        logger.warn("Failed to get file, got code ${httpResponse.status}")
        return false
    }

    val channel: ByteReadChannel = httpResponse.body()

    logger.trace("Writing to file")
    try {
        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())

            withContext(Dispatchers.IO) {
                while (packet.isNotEmpty) {
                    val bytes = packet.readBytes()
                    path.appendBytes(bytes)
                }
            }
        }
    } catch (e: IOException) {
        logger.warn("Failed to read contents from channel", e)
        return false
    }

    logger.trace("Successfully appended content to path")
    return true
}

@PublishedApi
internal val logger = LoggerFactory.getLogger(HttpClient::appendGetToPath.name)