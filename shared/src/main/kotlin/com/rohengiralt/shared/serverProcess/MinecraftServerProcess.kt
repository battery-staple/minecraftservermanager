package com.rohengiralt.shared.serverProcess

import com.rohengiralt.shared.serverProcess.ServerIO.Output
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Represents an individual process running a Minecraft server
 * and adds several facilities for interacting with such processes.
 * Lower level than [MinecraftServerCurrentRun](com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun).
 */
interface MinecraftServerProcess {
    /**
     * Contains all messages sent to the standard output and standard error of the server in best-effort chronological
     * order.
     * Note that the order is not guaranteed to be strictly chronological; however, it should be reasonably close.
     */
    val output: Flow<ProcessMessage<Output>>

    /**
     * A channel to which any messages sent will be relayed to the server process through the standard input.
     */
    val input: SendChannel<String>

    /**
     * Contains the messages both sent to and received from the server in best-effort chronological order.
     * Note that the order is not guaranteed to be strictly chronological, nor to match the exact order of `output`
     * or `input`; however, it should be reasonably close.
     */
    val interleavedIO: Flow<ProcessMessage<ServerIO>>

    /**
     * Attempts to stop the process running.
     * First attempts to allow the process to quit cleanly,
     * but will forcibly destroy the process if the timeout expires.
     * @param softTimeout the amount of time the process is allowed to take to clean up before being forcibly destroyed
     * @param additionalForcibleTimeout the amount of additional time to wait (after waiting for  [softTimeout]) for the
     *                                  process to return after attempting to destroy it forcibly.
     *                                  If this time expires without the process returning, this method will return null.
     * @return the process' exit code, or null if the process ends but its exit code is unknown
     * @throws StopFailed if both timeouts expire without the process returning
     */
    suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int?

    @Suppress("JavaIoSerializableObjectMustHaveReadResolve")
    data object StopFailed : Exception()

    /**
     * A message from or to the process; either IO or a special marker representing the end of the process.
     */
    sealed interface ProcessMessage<out T : ServerIO> {
        /**
         * A message either to or from the server.
         * @see ServerIO
         */
        @JvmInline
        value class IO<out T : ServerIO>(val content: T) : ProcessMessage<T>

        /**
         * Marker object representing the end of a process.
         * Once this object is sent to [output] and/or [interleavedIO], the process has completed.
         * @param code the exit code of the process, or null if not known
         */
        data class ProcessEnd(val code: Int?) : ProcessMessage<Nothing>
    }
}

