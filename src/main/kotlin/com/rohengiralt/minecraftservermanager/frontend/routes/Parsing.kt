package com.rohengiralt.minecraftservermanager.frontend.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import kotlinx.serialization.SerializationException
import java.util.*

internal fun uuidFromStringOrBadRequest(string: String): UUID = //TODO: Static extension of UUID when introduced
    try {
        UUID.fromString(string)
    } catch (e: IllegalArgumentException) {
        null
    } ?: throw BadRequestException("Expected UUID, got $string")

internal fun String.parseUUIDOrBadRequest(): UUID = uuidFromStringOrBadRequest(this)

internal suspend inline fun <reified T> ApplicationCall.receiveSerializable(): T = try {
    receive()
} catch (e: SerializationException) {
    throw BadRequestException("Couldn't parse body")
}