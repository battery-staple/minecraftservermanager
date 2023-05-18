package com.rohengiralt.minecraftservermanager.domain.model.local

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class MinecraftServerProcess(private val name: String, private val process: Process) {
    val output: Flow<ServerOutput> get() = _outputFlow.asSharedFlow()
    val input: SendChannel<String> get() = _input


    @OptIn(ExperimentalTime::class)
    suspend fun stop(timeout: Duration) = withContext(Dispatchers.IO) @Suppress("BlockingMethodInNonBlockingContext") {
        withTimeout(timeMillis = timeout.inWholeMilliseconds) {
            try {
                process.waitFor()
            } finally {
                process.destroyForcibly()
            }
        }
    }

    private val _input: Channel<String?> = Channel<String?>()
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
                        append("${input?.trimEnd()}\n")
                        flush()
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
//                        println("cache: ${_outputFlow.replayCache}")
                    } catch (e: IOException) {
                        println("Cannot read server output")
                        return@coroutineScope
                    }
                }
        } catch (e: Throwable) {
            println("Stream threw error $e")
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
        } catch (e: Throwable) {
            if (e !is CancellationException) println("Process ended with error $e")
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    init { // MUST be at end so that all properties used in jobs are initialized
        scope.launch {
            process.inputChannelJob()
        }
        scope.launch {
            process.outputChannelJob()
        }
        scope.launch {
            process.endJob()
        }
    }

    sealed interface ServerOutput

    @JvmInline
    value class TextOutput(val text: String) : ServerOutput
    object OutputEnd : ServerOutput
}