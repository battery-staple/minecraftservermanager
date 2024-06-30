package com.rohengiralt.minecraftservermanager.frontend.routes

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Failure
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Success
import com.rohengiralt.minecraftservermanager.plugins.ConflictException
import com.rohengiralt.minecraftservermanager.plugins.InternalServerException
import io.ktor.server.plugins.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Returns the content of a successful [APIResult],
 * or throws an appropriate HTTP Exception on [Failure].
 */
@OptIn(ExperimentalContracts::class)
fun <T> APIResult<T>.orThrow(): T {
    contract {
        returns() implies (this@orThrow is Success)
    }

    return when (this) {
        is Success -> content
        is Failure.Unknown -> throw InternalServerException()
        is Failure.MainResourceNotFound -> throw NotFoundException("Could not find ${resourceUUID ?: "the requested resource"}")
        is Failure.AuxiliaryResourceNotFound -> throw BadRequestException("Missing parameter $resourceUUID")
        is Failure.AlreadyExists -> throw ConflictException()
    }
}

/**
 * Returns the content of a successful [APIResult],
 * or returns the value of executing [block] on [Failure].
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> APIResult<T>.orElse(block: (Failure) -> T): T {
    contract {
        callsInPlace(block, InvocationKind.AT_MOST_ONCE)
    }

    return when (this) {
        is Success -> this.content
        is Failure -> block(this)
    }
}