package com.rohengiralt.minecraftservermanager.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration

context(CoroutineScope)
@OptIn(ExperimentalContracts::class)
suspend inline fun <T> tryWithBackoff(
    initialBackoff: Duration,
    onRestart: (ex: Exception, attempt: Int) -> Unit = { _, _ -> },
    action: (attempt: Int) -> T
): T {
    contract {
        callsInPlace(action, InvocationKind.AT_LEAST_ONCE)
    }

    try {
        return action(1)
    } catch (ex: Exception) {
        onRestart(ex, 1)
    }

    var backoff = initialBackoff
    var attempts = 1
    while (true) {
        ensureActive()
        val attempt = ++attempts

        logger.trace("Operation failed on attempt {}. Trying again after {}", attempt, backoff)
        delay(backoff)

        try {
            return action(attempt)
        } catch (ex: Exception) {
            onRestart(ex, attempt)
        }

        backoff *= 2
    }
}

@PublishedApi
internal val logger = LoggerFactory.getLogger("Backoff")