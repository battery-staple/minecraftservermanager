package com.rohengiralt.minecraftservermanager.util.extensions.httpClient

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.appendBytes

suspend inline fun HttpClient.appendGetToFile(urlString: String, file: Path): Boolean {
    val httpResponse = get(urlString)
    if (!httpResponse.status.isSuccess()) {
        println("get failed with code ${httpResponse.status}")
        return false
    }

    val channel: ByteReadChannel = httpResponse.body()

    println("Writing to file")
    while (!channel.isClosedForRead) {
        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())

        @Suppress("BlockingMethodInNonBlockingContext")
        withContext(Dispatchers.IO) {
            while (packet.isNotEmpty) {
                val bytes = packet.readBytes()
                file.appendBytes(bytes)
            }
        }
    }
    println("Appended content to file")

    return true
}