package com.rohengiralt.minecraftservermanager.util.routes

import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import io.ktor.server.plugins.*
import java.util.*

private fun uuidFromStringOrNull(string: String): UUID? = //TODO: Static extension of UUID when introduced
    try {
        UUID.fromString(string)
    } catch (e: IllegalArgumentException) {
        null
    }

private fun uuidFromStringOrBadRequest(string: String): UUID = //TODO: Static extension of UUID when introduced
    uuidFromStringOrNull(string) ?: throw BadRequestException("Expected UUID, got $string")

private fun String.parseUUIDOrNull(): UUID? = uuidFromStringOrNull(this)
private fun String.parseUUIDOrBadRequest(): UUID = uuidFromStringOrBadRequest(this)

// region ServerUUID
internal fun serverUUIDFromStringOrNull(string: String): ServerUUID? =
    uuidFromStringOrNull(string)?.let(::ServerUUID)

internal fun serverUUIDFromStringOrBadRequest(string: String): ServerUUID =
    uuidFromStringOrBadRequest(string).let(::ServerUUID)

internal fun String.parseServerUUIDOrNull(): ServerUUID? = serverUUIDFromStringOrNull(this)
internal fun String.parseServerUUIDOrBadRequest(): ServerUUID = serverUUIDFromStringOrBadRequest(this)

// endregion
// region RunnerUUID

internal fun runnerUUIDFromStringOrNull(string: String): RunnerUUID? =
    uuidFromStringOrNull(string)?.let(::RunnerUUID)

internal fun runnerUUIDFromStringOrBadRequest(string: String): RunnerUUID =
    uuidFromStringOrBadRequest(string).let(::RunnerUUID)

internal fun String.parseRunnerUUIDOrNull(): RunnerUUID? = runnerUUIDFromStringOrNull(this)
internal fun String.parseRunnerUUIDOrBadRequest(): RunnerUUID = runnerUUIDFromStringOrBadRequest(this)

// endRegion
// region RunUUID

internal fun runUUIDFromStringOrNull(string: String): RunUUID? =
    uuidFromStringOrNull(string)?.let(::RunUUID)

internal fun runUUIDFromStringOrBadRequest(string: String): RunUUID =
    uuidFromStringOrBadRequest(string).let(::RunUUID)

internal fun String.parseRunUUIDOrNull(): RunUUID? = runUUIDFromStringOrNull(this)
internal fun String.parseRunUUIDOrBadRequest(): RunUUID = runUUIDFromStringOrBadRequest(this)

// endregion