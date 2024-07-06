package com.rohengiralt.shared.util

/**
 * True if Kotlin assertions are enabled; i.e., if the `assert` method does not act as a no-op.
 */
val assertsEnabled by lazy {
    // Implementation note: this relatively expensive implementation was deliberately chosen over returning
    // `[some class].javaClass.desiredAssertionStatus()` because on the JVM it is possible to enable assertions
    // only for particular objects whereas Kotlin only respects global assertion status.
    // Thus, it's safer to just check if assertions work directly.

    try {
        assert(false)
        false
    } catch (e: AssertionError) {
        true
    }
}