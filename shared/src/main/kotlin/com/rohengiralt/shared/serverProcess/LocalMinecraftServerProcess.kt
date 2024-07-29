package com.rohengiralt.shared.serverProcess

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.io.InputStream
import kotlin.time.Duration

/**
 * A [MinecraftServerProcess] that wraps a Java [Process] running on the local machine.
 * @param serverName the name of the server this process belongs to
 * @param process the process being wrapped
 */
class LocalMinecraftServerProcess(serverName: String, private val process: Process) : PipingMinecraftServerProcess(serverName) {

    override suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeout = softTimeout) {
                process.destroy()
                process.waitFor()
            } ?: withTimeoutOrNull(timeout = additionalForcibleTimeout) {
                process.destroyForcibly()
                process.waitFor()
            }
        }

    override suspend fun trySend(input: String) {
        val outputStream = process.outputStream
        if (outputStream == null) {
            logger.error("Cannot write to process stdin; could not get output stream") // TODO: propagate this error to the user?
            return
        }

        outputStream
            .bufferedWriter()
            .run {
                logger.trace("Sending message to process")
                append(input + "\n") // newline is needed for the minecraft server
                                            // to consider it a new command
                flush() // Ensure it's sent to the server immediately; don't want to wait for more messages
            }
    }

    override val stdOut: Flow<String>?
        get() = getOutputForStream(process.inputStream, "stdout")
    override val stdError: Flow<String>?
        get() = getOutputForStream(process.errorStream, "stderr")

    private fun getOutputForStream(stream: InputStream?, streamName: String): Flow<String>? {
        logger.trace("Piping output for process $streamName")
        if (stream == null) {
            logger.error("Cannot read from process $streamName; could not get stream") // TODO: propagate this error to the user?
            return null
        }

        return stream
            .bufferedReader()
            .lineSequence()
            .asFlow()
    }

    override suspend fun waitForExit() = withContext(Dispatchers.IO) {
        process.waitFor()
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    init { // This init block MUST be at end so that all properties used in jobs are initialized
        initIO()
    }
}