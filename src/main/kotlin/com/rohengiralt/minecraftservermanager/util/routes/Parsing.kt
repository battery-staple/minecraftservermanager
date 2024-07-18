package com.rohengiralt.minecraftservermanager.util.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import org.slf4j.LoggerFactory

internal suspend inline fun <reified T> ApplicationCall.receiveSerializable(): T = try {
    receive()
} catch (e: Exception) { // receive can throw various exceptions, not just SerializationException
    logger.warn("Failed to receive serializable, got exception $e")
    throw BadRequestException("Couldn't parse body")
}

private val logger = LoggerFactory.getLogger("Utils")