package com.rohengiralt.shared.serverProcess

/**
 * Represents an IO message sent to or received from the server
 */
sealed interface ServerIO {
    val text: String

    /**
     * Represents a server IO message received **from** the server
     */
    sealed interface Output : ServerIO {
        /**
         * Represents a server IO message received from the server's standard output
         */
        @JvmInline
        value class LogMessage(override val text: String) : Output

        /**
         * Represents a server IO message received from the server's standard error
         */
        @JvmInline
        value class ErrorMessage(override val text: String) : Output
    }

    /**
     * Represents a server IO message sent **to** the server
     */
    sealed interface Input : ServerIO {
        /**
         * Represents a server IO message sent to the server's standard input
         */
        @JvmInline
        value class InputMessage(override val text: String) : Input
    }
}