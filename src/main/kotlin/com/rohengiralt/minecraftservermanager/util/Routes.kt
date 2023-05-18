package com.rohengiralt.minecraftservermanager.util

import io.ktor.server.application.*
import io.ktor.server.plugins.*

fun missingParameter(name: String): Nothing = throw BadRequestException("Missing parameter $name")
fun ApplicationCall.getParameterOrBadRequest(name: String): String =
    parameters[name]?: missingParameter(name)