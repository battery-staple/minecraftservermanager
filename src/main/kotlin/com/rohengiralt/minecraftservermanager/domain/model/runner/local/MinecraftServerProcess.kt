package com.rohengiralt.minecraftservermanager.domain.model.runner.local

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import org.slf4j.LoggerFactory
import java.io.IOException
import kotlin.time.Duration

class MinecraftServerProcess(private val name: String, private val process: Process) {
    val output: Flow<ServerOutput> get() = _outputFlow.asSharedFlow()
    val input: SendChannel<String> get() = _input

    suspend fun stop(softTimeout: Duration, additionalForcibleTimeout: Duration): Int? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeout = softTimeout) {
            process.destroy()
            process.waitFor()
        } ?: withTimeoutOrNull(timeout = additionalForcibleTimeout) {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private val _input: Channel<String?> = Channel()
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
                            append("${input?.trimEnd()}\n")
                            flush()
                        } catch (e: IOException) {
                            logger.warn("Cannot send input $input to server, got exception $e")
                        }
                    }
                    .also { logger.trace("Sent input to server: $input") }
            }
    }

    private val _outputFlow: MutableSharedFlow<ServerOutput> = MutableSharedFlow(Channel.UNLIMITED)
    private suspend fun Process.outputChannelJob() = coroutineScope {
        logger.info("Output channel job started")
        try {
            (inputStream ?: return@coroutineScope logger.error("Cannot read from process stdout; could not get input stream")) // TODO: propagate this error to the user?
                .bufferedReader()
                .lineSequence()
                .forEach {
                    try {
                        logger.debug("[SERVER $name]: $it")
                        _outputFlow.emit(TextOutput(it))
                    } catch (e: IOException) {
                        logger.warn("Cannot read server output, got exception $e")
                        return@coroutineScope
                    }
                }
        } catch (e: CancellationException) {
            logger.info("Output channel job cancelled")
            return@coroutineScope
        } catch (e: Throwable) {
            logger.error("Output stream threw error $e")
            return@coroutineScope
        } finally {
            logger.info("Stream ended")
            @OptIn(ExperimentalCoroutinesApi::class)
            _outputFlow.resetReplayCache()
            _outputFlow.emit(OutputEnd)
        }
    }

    private fun Process.endJob() {
        try {
            waitFor()
            logger.info("Minecraft Server (name: $name) ended with exit code ${exitValue()}")
            cancelAllJobs()
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                logger.error("Process ended with error $e")
                cancelAllJobs()
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val jobs = mutableListOf<Job>()
    init { // MUST be at end so that all properties used in jobs are initialized
        jobs += scope.launch {
            process.inputChannelJob()
        }
        jobs += scope.launch {
            process.outputChannelJob()
        }
        jobs += scope.launch {
            process.endJob()
        }
    }

    private fun cancelAllJobs() = jobs.forEach { job -> job.cancel() }

    sealed interface ServerOutput

    @JvmInline
    value class TextOutput(val text: String) : ServerOutput
    data object OutputEnd : ServerOutput

    private val logger = LoggerFactory.getLogger(this::class.java)
}