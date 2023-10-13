package com.rohengiralt.minecraftservermanager.util.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import java.util.*

internal fun uuidFromStringOrNull(string: String): UUID? = //TODO: Static extension of UUID when introduced
    try {
        UUID.fromString(string)
    } catch (e: IllegalArgumentException) {
        null
    }

internal fun uuidFromStringOrBadRequest(string: String): UUID = //TODO: Static extension of UUID when introduced
    uuidFromStringOrNull(string) ?: throw BadRequestException("Expected UUID, got $string")

internal fun String.parseUUIDOrNull(): UUID? = uuidFromStringOrNull(this)
internal fun String.parseUUIDOrBadRequest(): UUID = uuidFromStringOrBadRequest(this)

internal suspend inline fun <reified T> ApplicationCall.receiveSerializable(): T = try {
    receive()
} catch (e: Exception) { // receive can throw various exceptions, not just SerializationException
    println("Failed to receive serializable, got exception $e")
    throw BadRequestException("Couldn't parse body")
}