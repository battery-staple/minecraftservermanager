package com.rohengiralt.minecraftservermanager.domain.model.local

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import java.io.IOException
import kotlin.time.Duration

class MinecraftServerProcess(private val name: String, private val process: Process) {
    val output: Flow<ServerOutput> get() = _outputFlow.asSharedFlow()
    val input: SendChannel<String> get() = _input

    suspend fun stop(softTimeout: Duration, forcibleTimeout: Duration): Int? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeout = softTimeout) {
            process.destroy()
            process.waitFor()
        } ?: withTimeoutOrNull(timeout = forcibleTimeout) {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private val _input: Channel<String?> = Channel()
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun Process.inputChannelJob() {
        println("Input channel job started")
        _input
            .consumeAsFlow()
            .flowOn(Dispatchers.IO)
            .collect { input ->
                println("process got new input $input")
                (outputStream ?: return@collect println("No output stream"))
                    .bufferedWriter()
                    .run {
                        try {
                            append("${input?.trimEnd()}\n")
                            flush()
                        } catch (e: IOException) {
                            println("Cannot send input $input to server, got exception $e")
                        }
                    }
                    .also { println("Inputted $input") }
            }
    }

    private val _outputFlow: MutableSharedFlow<ServerOutput> = MutableSharedFlow(Channel.UNLIMITED)
    private suspend fun Process.outputChannelJob() = coroutineScope {
        println("Piping output")
        try {
            (inputStream ?: return@coroutineScope println("No output stream"))
                .bufferedReader()
                .lineSequence()
                .forEach {
                    try {
                        println("[SERVER $name]: $it")
                        _outputFlow.emit(TextOutput(it))
                    } catch (e: IOException) {
                        println("Cannot read server output, got exception $e")
                        return@coroutineScope
                    }
                }
        } catch (e: CancellationException) {
            println("Output channel job cancelled")
            return@coroutineScope
        } catch (e: Throwable) {
            println("Stream threw error $e")
            return@coroutineScope
        } finally {
            println("Stream ended")
            @OptIn(ExperimentalCoroutinesApi::class)
            _outputFlow.resetReplayCache()
            _outputFlow.emit(OutputEnd)
        }
    }

    private fun Process.endJob() {
        try {
            waitFor()
            println("Minecraft Server process ended with exit code ${exitValue()}")
            cancelAllJobs()
        } catch (e: Throwable) {
            if (e !is CancellationException) {
                println("Process ended with error $e")
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
    object OutputEnd : ServerOutput
}