package com.rohengiralt.shared.serverProcess

import com.rohengiralt.shared.util.assertAllPropertiesNotNull
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

abstract class PipingMinecraftServerProcess(protected val serverName: String) : MinecraftServerProcess {
    override val output: Flow<MinecraftServerProcess.ProcessMessage<ServerIO.Output>> by lazy {
        assertInv()
        _output.asSharedFlow()
    }

    override val input: SendChannel<String> by lazy {
        assertInv()
        _input
    }
    override val interleavedIO: Flow<MinecraftServerProcess.ProcessMessage<ServerIO>> by lazy {
        assertInv()
        _interleavedIO.asSharedFlow()
    }

    /**
     * The [MutableSharedFlow] that underlies [interleavedIO].
     * This field is necessary to allow sending to the flow from within this class but not from outside.
     */
    private val _interleavedIO: MutableSharedFlow<MinecraftServerProcess.ProcessMessage<ServerIO>> =
        MutableSharedFlow(Channel.UNLIMITED)

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
    private val _output: MutableSharedFlow<MinecraftServerProcess.ProcessMessage<ServerIO.Output>> =
        MutableSharedFlow(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO) // All jobs are likely to block often, so Dispatchers.IO is best
    private val jobs = mutableListOf<Job>()
    private val jobsAreInitialized = AtomicBoolean()
    private val logger =
        LoggerFactory.getLogger(PipingMinecraftServerProcess::class.java) // not this; we want to disambiguate between sub- and superclass operations

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
                    _interleavedIO.emit(
                        MinecraftServerProcess.ProcessMessage.IO(
                            ServerIO.Input.InputMessage(
                                cleanedInput
                            )
                        )
                    )

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
        launch { pipeOutputJob(stdOut, createMessage = ServerIO.Output::LogMessage, streamName = "stdout") }
        launch { pipeOutputJob(stdError, createMessage = ServerIO.Output::ErrorMessage, streamName = "stderr") }
        logger.debug("Output channel job ended")
    }

    /**
     * Helper method to pipe from an output stream into [output] and [interleavedIO].
     */
    private suspend fun pipeOutputJob(output: Flow<String>?, createMessage: (String) -> ServerIO.Output, streamName: String) {
        try {
            if (output == null) {
                logger.error("Cannot read from stream $streamName")
                return
            }

            output.collect {
                try {
                    logger.trace("[SERVER $serverName $streamName]: $it")

                    _output.emit(MinecraftServerProcess.ProcessMessage.IO(createMessage(it)))

                    _interleavedIO.emit(MinecraftServerProcess.ProcessMessage.IO(createMessage(it)))
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
     * A flow that contains each message in the server's standard out as it is sent.
     */
    protected abstract val stdOut: Flow<String>?

    /**
     * A flow that contains each message in the server's standard error as it is sent.
     */
    protected abstract val stdError: Flow<String>?

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
            _output.emit(MinecraftServerProcess.ProcessMessage.ProcessEnd(status))
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