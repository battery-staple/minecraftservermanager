package com.rohengiralt.monitor.serverProcess

import com.rohengiralt.monitor.serverProcess.MinecraftServerProcess.ProcessMessage
import com.rohengiralt.monitor.serverProcess.ServerIO.Input.InputMessage
import com.rohengiralt.monitor.serverProcess.ServerIO.Output
import com.rohengiralt.monitor.util.assertAllPropertiesNotNull
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
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
     * @return the process' exit code, or null if both timeouts expire without the process returning
     */
    suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int?

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
         */
        data object ProcessEnd : ProcessMessage<Nothing>
    }
}

/**
 * Constructs a new [MinecraftServerProcess] instance
 * @param process the underlying Java process to wrap
 */
fun MinecraftServerProcess(process: Process): MinecraftServerProcess =
    MinecraftServerProcessImpl(process)

private class MinecraftServerProcessImpl(private val process: Process) : MinecraftServerProcess {
    override val output: Flow<ProcessMessage<Output>> get() = _output.asSharedFlow()
    override val input: SendChannel<String> get() = _input
    override val interleavedIO: Flow<ProcessMessage<ServerIO>> get() = _interleavedIO.asSharedFlow()

    override suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeout = softTimeout) {
            process.destroy()
            process.waitFor()
        } ?: withTimeoutOrNull(timeout = additionalForcibleTimeout) {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    /**
     * The [MutableSharedFlow] that underlies [interleavedIO].
     * This field is necessary to allow sending to the flow from within this class but not from outside.
     */
    private val _interleavedIO: MutableSharedFlow<ProcessMessage<ServerIO>> = MutableSharedFlow(Channel.UNLIMITED)

    /**
     * The [Channel] that underlies [input].
     * This field is necessary to allow sending to the channel from within this class but to discourage doing the same
     * from the outside.
     */
    private val _input: Channel<String> = Channel()

    /**
     * The job that handles piping from [input] into the process' standard input and [interleavedIO].
     */
    private suspend fun Process.inputChannelJob() {
        logger.info("Input channel job started")
        _input
            .consumeAsFlow()
            .flowOn(Dispatchers.IO)
            .collect { input ->
                logger.debug("Process got new input: $input")
                (outputStream ?: return@collect logger.error("Cannot write to process stdin; could not get output stream")) // TODO: propagate this error to the user?
                    .bufferedWriter()
                    .run {
                        try {
                            val cleanedInput = input.trimEnd()
                            logger.trace("Sending message to interleavedIO")
                            _interleavedIO.emit(ProcessMessage.IO(InputMessage(cleanedInput)))
                            logger.trace("Sending message to process")
                            append(cleanedInput + "\n") // newline is needed for the minecraft server
                                                        // to consider it a new command
                            flush() // Ensure it's sent to the server immediately; don't want to wait for more messages
                        } catch (e: IOException) {
                            logger.warn("Cannot send input $input to server, got exception $e")
                        }
                    }
                    .also { logger.trace("Sent input to server: $input") }
            }
    }

    /**
     * The [MutableSharedFlow] that underlies [output].
     * This field is necessary to allow sending to the flow from within this class but not from outside.
     */
    private val _output: MutableSharedFlow<ProcessMessage<Output>> = MutableSharedFlow(Channel.UNLIMITED)

    /**
     * The job that handles piping from the process' standard output
     * and standard error into [output] and [interleavedIO].
     */
    private suspend fun Process.outputChannelJob() = coroutineScope {
        logger.info("Output channel job started")
        launch { pipeOutputJob(stream = inputStream, createMessage = Output::LogMessage, streamName = "stdout") }
        launch { pipeOutputJob(stream = errorStream, createMessage = Output::ErrorMessage, streamName = "stderr") }
        logger.debug("Output channel job ended")
    }

    /**
     * Helper method to pipe from an output stream into [output] and [interleavedIO].
     */
    private suspend fun pipeOutputJob(stream: InputStream?, createMessage: (String) -> Output, streamName: String) {
        logger.trace("Piping output for output stream $streamName")
        try {
            (stream ?: return logger.error("Cannot read from process $streamName; could not get stream")) // TODO: propagate this error to the user?
                .bufferedReader()
                .lineSequence()
                .forEach {
                    try {
                        logger.debug("[SERVER OUT]: $it")

                        logger.trace("Sending message to output")
                        _output.emit(ProcessMessage.IO(createMessage(it)))

                        logger.trace("Sending message to interleavedIO")
                        _interleavedIO.emit(ProcessMessage.IO(createMessage(it)))
                    } catch (e: IOException) {
                        logger.warn("Cannot read server output, got exception $e")
                    }
                }
        } catch (e: CancellationException) {
            logger.info("Output channel job for $streamName cancelled")
        } catch (e: Throwable) {
            logger.error("Output stream $streamName threw error $e")
        } finally {
            logger.info("Output stream $streamName ended")
            @OptIn(ExperimentalCoroutinesApi::class)
            _output.resetReplayCache()
            _output.emit(ProcessMessage.ProcessEnd)
        }
    }

    /**
     * The job that handles cleanup when the process ends.
     */
    private fun Process.endJob() {
        try {
            waitFor()
            logger.info("Minecraft Server ended with exit code ${exitValue()}")
            cancelAllJobs()
        } catch (e: CancellationException) {
            logger.info("Process end job cancelled")
        } catch (e: Throwable) {
            logger.error("Process ended with error $e")
            cancelAllJobs()
        }
    }

    /**
     * Helper method to cancel all running jobs to avoid wasting resources after the process ends.
     */
    private fun cancelAllJobs() {
        @Suppress("ControlFlowWithEmptyBody")
        while (!jobsAreInitialized.get()) {} // TODO: Remove inefficient spin loop
        jobs.forEach { job -> job.cancel() }
    }

    private val scope = CoroutineScope(Dispatchers.IO) // All jobs are likely to block often, so Dispatchers.IO is best
    private val jobs = mutableListOf<Job>()
    private val jobsAreInitialized = AtomicBoolean()
    private val logger = LoggerFactory.getLogger(this::class.java)

    init { // This init block MUST be at end so that all properties used in jobs are initialized
        assertAllPropertiesNotNull() // Ensure all properties *are* initialized

        jobs += scope.launch { process.inputChannelJob() }
        jobs += scope.launch { process.outputChannelJob() }
        jobs += scope.launch { process.endJob() }

        jobsAreInitialized.set(true)
        logger.debug("Launched all jobs")
    }

}