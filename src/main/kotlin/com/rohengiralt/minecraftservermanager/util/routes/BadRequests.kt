package com.rohengiralt.minecraftservermanager.util.routes

import io.ktor.server.application.*
import io.ktor.server.plugins.*

fun missingField(name: String): Nothing = throw BadRequestException("Missing field $name")
fun cannotUpdateField(name: String): Nothing = throw BadRequestException("Cannot update field $name")
fun ApplicationCall.getParameterOrBadRequest(name: String): String =
    parameters[name]?: missingField(name)