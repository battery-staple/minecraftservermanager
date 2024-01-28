package com.rohengiralt.minecraftservermanager.util

import org.slf4j.LoggerFactory
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.time.measureTime

/**
 * Asserts that all non-nullable properties of the given object are in fact not `null`.
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
                if (!property.returnType.isMarkedNullable) {
                    property.isAccessible = true

                    assert(property.get(this) !== null) {
                        "Property `${property.name}` has not yet been initialized!"
                    }
                }
            }
        }.also { logger.trace("Took {} to confirm all properties of {} not null", it, this) }
    }
}

@PublishedApi
internal val logger = LoggerFactory.getLogger("AssertPropertiesNotNull")