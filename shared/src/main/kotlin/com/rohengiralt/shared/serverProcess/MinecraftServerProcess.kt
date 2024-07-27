package com.rohengiralt.shared.serverProcess

import com.rohengiralt.shared.serverProcess.MinecraftServerProcess.ProcessMessage
import com.rohengiralt.shared.serverProcess.ServerIO.Input.InputMessage
import com.rohengiralt.shared.serverProcess.ServerIO.Output
import com.rohengiralt.shared.util.assertAllPropertiesNotNull
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.io.IOException
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
         * @param code the exit code of the process, or null if not known
         */
        data class ProcessEnd(val code: Int?) : ProcessMessage<Nothing>
    }
}

abstract class PipingMinecraftServerProcess(private val serverName: String) : MinecraftServerProcess {
    override val output: Flow<ProcessMessage<Output>> by lazy {
        assertInv()
        _output.asSharedFlow()
    }

    override val input: SendChannel<String> by lazy {
        assertInv()
        _input
    }
    override val interleavedIO: Flow<ProcessMessage<ServerIO>> by lazy {
        assertInv()
        _interleavedIO.asSharedFlow()
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
     * The [MutableSharedFlow] that underlies [output].
     * This field is necessary to allow sending to the flow from within this class but not from outside.
     */
    private val _output: MutableSharedFlow<ProcessMessage<Output>> = MutableSharedFlow(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO) // All jobs are likely to block often, so Dispatchers.IO is best
    private val jobs = mutableListOf<Job>()
    private val jobsAreInitialized = AtomicBoolean()
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The job that handles piping from [input] into the process' standard input and [interleavedIO].
     */
    private suspend fun inputChannelJob() {
        logger.info("Input channel job started")
        _input
            .consumeAsFlow()
            .flowOn(Dispatchers.IO)
            .collect { input ->
                try {
                    logger.trace("Got new input: $input")
                    val cleanedInput = input.trimEnd()

                    trySend(cleanedInput)

                    logger.trace("Sending input message to interleavedIO")
                    _interleavedIO.emit(ProcessMessage.IO(InputMessage(cleanedInput)))

                    logger.debug("Sent input: $input")
                } catch (e: IOException) {
                    logger.warn("Cannot send input $input", e)
                }
            }
    }

    /**
     * Sends a message to the minecraft server, or throws an IOException if not possible.
     */
    protected abstract suspend fun trySend(input: String)

    /**
     * The job that handles piping from the process' standard output
     * and standard error into [output] and [interleavedIO].
     */
    private suspend fun outputChannelJob() = coroutineScope {
        logger.info("Output channel job started")
        launch { pipeOutputJob(getStdOut(), createMessage = Output::LogMessage, streamName = "stdout") }
        launch { pipeOutputJob(getStdErr(), createMessage = Output::ErrorMessage, streamName = "stderr") }
        logger.debug("Output channel job ended")
    }

    /**
     * Helper method to pipe from an output stream into [output] and [interleavedIO].
     */
    private suspend fun pipeOutputJob(output: Flow<String>?, createMessage: (String) -> Output, streamName: String) {
        try {
            if (output == null) {
                logger.error("Cannot read from stream $streamName")
                return
            }

            output.collect {
                try {
                    logger.debug("[SERVER $streamName $serverName]: $it")

                    logger.trace("Sending message to output")
                    _output.emit(ProcessMessage.IO(createMessage(it)))

                    logger.trace("Sending output message to interleavedIO")
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
        }
    }

    /**
     * Returns a flow that contains each message in the server's standard out as it is sent.
     */
    protected abstract suspend fun getStdOut(): Flow<String>?

    /**
     * Returns a flow that contains each message in the server's standard error as it is sent.
     */
    protected abstract suspend fun getStdErr(): Flow<String>?

    /**
     * The job that handles cleanup when the process ends.
     */
    private suspend fun endJob() {
        var status: Int? = null
        try {
            status = withContext(Dispatchers.IO) {
                waitForExit()
            }
            logger.info("Minecraft Server ended with exit code ${status ?: "unknown"}")
            cancelAllJobs()
        } catch (e: CancellationException) {
            logger.info("Process end job cancelled")
        } catch (e: Throwable) {
            logger.error("Process ended with error $e")
            cancelAllJobs()
        } finally {
            @OptIn(ExperimentalCoroutinesApi::class)
            _output.resetReplayCache()
            _output.emit(ProcessMessage.ProcessEnd(status))
        }
    }

    /**
     * Suspends until the server ends.
     * @return the server's exit code, or null if unknown
     */
    protected abstract suspend fun waitForExit(): Int?

    /**
     * Helper method to cancel all running jobs to avoid wasting resources after the process ends.
     */
    private fun cancelAllJobs() {
        @Suppress("ControlFlowWithEmptyBody")
        while (!jobsAreInitialized.get()) {} // TODO: Remove inefficient spin loop
        jobs.forEach { job -> job.cancel() }
    }

    /**
     * Begins piping to and from [input], [output], and [interleavedIO].
     * Precondition: if this function is called at class construction,
     * it must be called at the end of the constructor after all properties are initialized.
     * If this method is not called by a subclass, it will be automatically called
     * on first access of [input], [output], or [interleavedIO]
     */
    protected fun initIO() {
        assertAllPropertiesNotNull() // Ensure all properties are initialized

        jobs += scope.launch { inputChannelJob() }
        jobs += scope.launch { outputChannelJob() }
        jobs += scope.launch { endJob() }

        jobsAreInitialized.set(true)
        logger.debug("Launched all jobs")
    }

    private fun assertInv() {
        assert(jobsAreInitialized.get()) { "Jobs not yet all initialized" }
    }
}