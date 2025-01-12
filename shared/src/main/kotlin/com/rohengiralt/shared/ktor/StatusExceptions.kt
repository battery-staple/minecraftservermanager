package com.rohengiralt.shared.ktor

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun StatusPagesConfig.configureStatusPagesExceptionHandling() {
    exception<Throwable> { call, e ->
        when (e) {
            is AuthenticationException -> call.respond(HttpStatusCode.Unauthorized, e.message ?: "Unauthorized")
            is AuthorizationException -> call.respond(HttpStatusCode.Forbidden, e.message ?: "Forbidden")
            is BadRequestException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "Bad request")
            is NotFoundException -> call.respond(HttpStatusCode.NotFound, e.message ?: "Not found")
            is ConflictException -> call.respond(HttpStatusCode.Conflict, e.message ?: "Conflict")
            is NotAllowedException -> call.respond(HttpStatusCode.MethodNotAllowed, e.message ?: "Not allowed")
            is NotImplementedError -> call.respond(HttpStatusCode.NotImplemented, e.message ?: "Not implemented")
            is InternalServerException -> call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal Server Error")
            else -> call.application.environment.log.error("Uncaught exception", e)
        }
    }
}

class AuthenticationException(message: String? = null) : RuntimeException(message)
class AuthorizationException(message: String? = null) : RuntimeException(message)
class ConflictException(message: String? = null) : RuntimeException(message)
class NotAllowedException(message: String? = null) : RuntimeException(message)
class InternalServerException(message: String? = null) : RuntimeException(message)