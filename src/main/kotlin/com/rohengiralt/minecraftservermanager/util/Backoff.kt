package com.rohengiralt.minecraftservermanager.util

import io.ktor.util.reflect.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import org.slf4j.LoggerFactory
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference
import kotlin.reflect.KClass
import kotlin.time.Duration

/**
 * A function to handle a restart after an operation fails.
 * @param ex the exception thrown that caused the failure
 * @param attempt the number of this attempt
 */
typealias RestartHandler = (ex: Exception, attempt: Int) -> Unit

/**
 * Attempts to run [action], returning its result.
 * If [action] throws an exception, retries it after a delay.
 * This continues until [action] succeeds, with the delay increasing exponentially.
 * This function is cancellable if [action] is.
 * @param initialBackoff the backoff after [action]'s first failure
 * @param onRestart a callback invoked after each failure
 * @param action the operation to attempt (and retry if necessary)
 */
context(CoroutineScope)
@OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)
@BuilderInference
suspend inline fun <T> tryWithBackoff(
    initialBackoff: Duration,
    onRestart: RestartHandler = { _: Exception, _: Int -> },
    action: (attempt: Int) -> T
): T {
    contract {
        callsInPlace(action, InvocationKind.AT_LEAST_ONCE)
    }

    try {
        return action(1)
    } catch (ex: Exception) {
        ensureActive()
        onRestart(ex, 1)
    }

    var backoff = initialBackoff
    forever { i ->
        ensureActive()
        val attempt = i + 2 // start from 2

        logger.trace("Operation failed on attempt {}. Trying again after {}", attempt, backoff)
        delay(backoff)

        try {
            return action(attempt)
        } catch (ex: Exception) {
            ensureActive()
            onRestart(ex, attempt)
        }

        backoff *= 2
    }
}

/**
 * Returns a [RestartHandler] that handles any of the specified [exceptions] by rethrowing them.
 * If the action in [tryWithBackoff] throws any of these exceptions, it will effectively not be caught.
 */
fun propagate(vararg exceptions: KClass<out Exception>): RestartHandler = { ex, _ ->
    if (exceptions.any(ex::instanceOf)) throw ex
}

/**
 * An overload of [propagate] that uses a reified type parameter instead of [KClass] argument.
 * @see propagate
 */
inline fun <reified T : Exception> propagate(): RestartHandler = propagate(T::class)

@PublishedApi
internal val logger = LoggerFactory.getLogger("Backoff")