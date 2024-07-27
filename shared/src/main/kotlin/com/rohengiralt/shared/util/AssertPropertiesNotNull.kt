package com.rohengiralt.shared.util

import org.slf4j.LoggerFactory
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.time.measureTime

/**
 * Asserts that all non-nullable non-lateinit properties of the given object are in fact not `null`.
 *
 * In typical operation, this should never throw, as the Kotlin type system should ensure
 * that non-nullable properties are never `null`. However, there are some edge cases in which
 * this is not true. For instance, `init` blocks may reference properties that have not yet
 * been initialized, causing an NPE at runtime with no compile time warning. This method
 * confirms this is not the case.
 */
inline fun <reified T : Any> T.assertAllPropertiesNotNull() {
    if (assertsEnabled) {
        measureTime {
            T::class.memberProperties.forEach { property ->
                if (!property.returnType.isMarkedNullable && !property.isLateinit) {
                    val backingField = property.javaField

                    // Only check properties with backing fields; getters can never return null.
                    // Also skip over property delegates (which are considered to be backing fields) for the same reason
                    if (backingField != null && property.accessDelegate(this) != null) {
                        backingField.isAccessible = true
                        assert(backingField.get(this) != null) {
                            "Property `${property.name}` has a backing field that is not yet initialized!"
                        }
                    }
                }
            }
        }.also { logger.trace("Took {} to confirm all properties of {} not null", it, this) }
    }
}

@PublishedApi
internal fun <T> KProperty1<T, *>.accessDelegate(receiver: T): Any? {
    isAccessible = true
    return getDelegate(receiver)
}

@PublishedApi
internal val logger = LoggerFactory.getLogger("AssertPropertiesNotNull")