package com.rohengiralt.minecraftservermanager.frontend.routes

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Failure
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Success
import com.rohengiralt.minecraftservermanager.plugins.InternalServerException
import io.ktor.server.plugins.*
import javax.annotation.CheckReturnValue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@CheckReturnValue
@OptIn(ExperimentalContracts::class)
fun <T> APIResult<T>.orThrow(): T {
    contract {
        returns() implies (this@orThrow is Success)
    }

    return when (this) {
        is Success -> content
        is Failure.Unknown -> throw InternalServerException()
        is Failure.NotFound -> throw NotFoundException()
    }
}