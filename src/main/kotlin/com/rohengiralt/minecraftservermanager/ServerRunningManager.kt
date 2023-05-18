//package com.rohengiralt.minecraftservermanager
//
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerRuntimeConfiguration
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerStorage
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.channels.SendChannel
//import kotlinx.coroutines.flow.*
//import kotlinx.datetime.Clock
//import kotlinx.datetime.Instant
//import kotlinx.serialization.KSerializer
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.descriptors.SerialDescriptor
//import kotlinx.serialization.descriptors.buildClassSerialDescriptor
//import kotlinx.serialization.encoding.Decoder
//import kotlinx.serialization.encoding.Encoder
//import java.io.IOException
//import java.nio.file.Path
//import kotlin.io.path.absolutePathString
//import kotlin.io.path.div
//import kotlin.io.path.writeText
//
////sealed interface ServerRun {
////    val server: MinecraftServer
////
////    var isRunning: Boolean
////    val output: Flow<ServerOutput>?
////    val input: SendChannel<String>?
////
////    companion object {
////        operator fun invoke(server: MinecraftServer): ServerRun = ServerRunImpl(server)
////    }
////}w
//
//class ServerProcessFactory(private val minSpaceMegabytes: UInt = 1024U, private val maxSpaceMegabytes: UInt = 2048U) {
//    fun getProcess(configuration: MinecraftServerRuntimeConfiguration): Process {
//        agreeToEula(directory = configuration.contentDirectory)
//
//        val process = ProcessBuilder("java", "-Xms${minSpaceMegabytes}M", "-Xmx${maxSpaceMegabytes}M", "-jar", configuration.jar.escapedAbsolutePathString())
//            .directory(configuration.contentDirectory.toFile())
//            .redirectErrorStream(true)
//            .start()
//            .also {
//                println("Started process $it")
//            }
//
//        GlobalScope.launch(Dispatchers.IO) {
//            process.endJob()
//        }
//        return process
//    }
//
//    private fun agreeToEula(directory: Path) {
//        (directory/"eula.txt") // Does this need to be run every time?
//            .writeText("eula=true")
//        println("Agreed to EULA")
//    }
//
//    private fun Process.endJob() {
//        try {
//            waitFor()
//            println("Minecraft Server process ended with exit code ${exitValue()}")
//        } catch (e: Throwable) {
//            if (e !is CancellationException) println("Process ended with error $e")
//        }
//    }
//
//    private suspend fun Process.logConsoleJob() = coroutineScope { // For debugging purposes only; TODO: Remove
//        launch {
//            inputStream.bufferedReader().lineSequence().forEach {
//                try {
//                    println("[MINECRAFT SERVER]: $it")
//                } catch (e: IOException) {
//                    println("Cannot read output of server")
//                }
//            }
//        }
//
//        launch {
//            errorStream.bufferedReader().lineSequence().forEach {
//                try {
//                    println("[MINECRAFT SERVER ERROR]: $it")
//                } catch (e: IOException) {
//                    println("Cannot read output of server")
//                }
//            }
//        }
//    }
//
//    private fun Path.escapedAbsolutePathString() =
//        absolutePathString().replace(" ", "\\ ")
//}
//
//
//
//@Serializable
//class ServerRuns1() {
////    val archive: MutableList<
//    val current: MutableList<ServerRun> = mutableListOf()
//
//    fun add(element: ServerRun): Boolean {
//        if (current.any { it is ServerRun.Ongoing }) return false // Parallel runs unsupported
//
////        element.isRunning = true //TODO: not a property; action on data class instead
//        return current.add(element)
//    }
//
//    fun forceAdd(element: ServerRun) {
//        current
//            .asSequence()
//            .filterIsInstance<ServerRun.Ongoing>()
//            .forEach(::archive)
//
////        element.isRunning = true
//        current.add(element)
//    }
//
//    fun archive(element: ServerRun) {
//        if (!current.remove(element)) throw NoSuchElementException()
////        element.isRunning = false
//    }
//
//    @Serializable(with = ServerRun.ServerRunSerializer::class)
//    sealed class ServerRun {
//        abstract val start: Instant
//        abstract val stop: Instant?
//        abstract val output: Flow<ServerOutput>
//
//        @Suppress("DataClassPrivateConstructor")
//        data class Ongoing private constructor(val process: Process): ServerRun() {
//            override val start: Instant = Clock.System.now()
//            override var stop: Instant? = null
//                private set
//
//            private val processJobs = mutableListOf<Job>()
//            private val processScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
//
//            init {
//                processJobs.forEach { it.cancel() }
//                with(processScope) {
//                    cancel()
//                    launch {
//                        process.outputChannelJob()
//                    }
//                    launch {
//                       process.inputChannelJob()
//                    }
//                }
//
//                processJobs.addAll(listOf(
//                    GlobalScope.launch {
//                        process.outputChannelJob()
//                    },
//                    GlobalScope.launch {
//                        process.inputChannelJob()
//                    }
//                ))
//            }
//
//            fun stop() {
//                process.destroy()
//                GlobalScope.launch(Dispatchers.IO) {
//                    withTimeout(5000L) { //TODO: No magic number
//                        try {
//                            process.waitFor()
//                        } finally {
//                            process.destroyForcibly()
//                            stop = Clock.System.now()
//                        }
//                    }
//                }
//            }
//       /*     private var process: Process? = null
//                set(value) {
//                    field = value?.apply {
//                        processJobs.forEach { it.cancel() }
//    //                    with(processScope) {
//    //                        cancel()
//    //                        launch {
//    //                            outputChannelJob()
//    //                        }
//    //                        launch {
//    //                            inputChannelJob()
//    //                        }
//    //                    }
//
//                        processJobs.addAll(listOf(
//                            GlobalScope.launch {
//                                outputChannelJob()
//                            },
//                            GlobalScope.launch {
//                                inputChannelJob()
//                            }
//                        ))
//                    }.also { new ->
//                        field?.run {
//                            if (new != null) {
//                                println("Replacing process $field with $new")
//                            } else {
//                                println("Destroying process $field")
//                            }
//                            destroy() // Should do something else here? Should it even be allowed?
//                        }
//                    }
//                }
//
//            var isRunning: Boolean
//                get() = process != null
//                set(value) {
//                    if (value) {
//                        if (isRunning) return
//                        println("Starting server")
//                        process = start(server.jar, server.port, server.configuration.directory) TODO!!!
//                        println("Started server")
//                    } else {
//                        process?.destroy()?.also { println("Stopping server") } ?: println("No server to stop")
//                    }
//                }
//
//            private fun start(jar: Path, port: Port, contentDirectory: Path, minSpaceMegabytes: UInt = 1024U, maxSpaceMegabytes: UInt = 2048U): Process { //TODO: Port
//                (contentDirectory/"eula.txt") // Does this need to be run every time?
//                    .writeText("eula=true")
//
//                println("Agreed to EULA")
//                val process = ProcessBuilder("java", "-Xms${minSpaceMegabytes}M", "-Xmx${maxSpaceMegabytes}M", "-jar", jar.escapedAbsolutePathString())
//                    .directory(contentDirectory.toFile())
//                    .redirectErrorStream(true)
//                    .start()
//
//                println("Started process $process")
//                return process
//            }*/
//
//            private suspend fun Process.outputChannelJob() {
//                coroutineScope {
//                    println("Piping output")
//                    launch(Dispatchers.IO) {
//                        try {
//                            (inputStream ?: return@launch println("No output stream"))
//                                .bufferedReader()
//                                .lineSequence()
//                                .asFlow() // Will this prevent blocking the thread when waiting?
//                                .flowOn(Dispatchers.IO)
//                                .collect {
//                                    try {
//                                        println("New server output $it")
//                                        _outputFlow.emit(TextOutput(it))
//                                    } catch (e: IOException) {
//                                        println("Cannot read server output")
//                                        cancel()
//                                    }
//                                }
//                        } catch (e: Throwable) {
//                            println("Stream threw error $e")
//                        } finally {
//                            println("Stream ended")
//                            @OptIn(ExperimentalCoroutinesApi::class)
//                            _outputFlow.resetReplayCache()
//                            _outputFlow.emit(OutputEnd)
//                        }
//                    }
//                }
//            }
//
//            private val _outputFlow: MutableSharedFlow<ServerOutput> = MutableSharedFlow(Channel.UNLIMITED)
//            override val output: Flow<ServerOutput> get() = _outputFlow.asSharedFlow()
//
//            private suspend fun Process.inputChannelJob() {
//                coroutineScope {
//                    launch(Dispatchers.IO) {
//                        _input
//                            .consumeAsFlow()
//                            .flowOn(Dispatchers.IO)
//                            .collect { input ->
//                                (outputStream ?: return@collect println("No output stream"))
//                                    .bufferedWriter()
//                                    .run {
//                                        append("${input.trimEnd()}\n")
//                                        flush()
//                                    }
//                                    .also { println("Inputted $input") }
//                            }
//                    }
//                }
//            }
//
//            private val _input: Channel<String> = Channel()
//            val input: SendChannel<String> = _input
//
//            protected fun finalize() {
//                println("Finalizing with process $process")
//                processJobs.forEach { it.cancel() }
//            }
//        }
//
//        companion object ServerRunSerializer : KSerializer<ServerRun> {
//            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ServerRun") {
////                element("start", )
//            }
//
//            override fun serialize(encoder: Encoder, value: ServerRun) {
////                when (this)
//            }
//
//            override fun deserialize(decoder: Decoder): ServerRun {
//                TODO("Not yet implemented")
//            }
//        }
//    }
//
//}
//
//sealed class ServerRun1(val server: MinecraftServerStorage) {
//    abstract val start: Instant?
//    abstract val stop: Instant?
//
//    abstract val isRunning: Boolean
//    abstract val output: Flow<ServerOutput>?
//    abstract val input: SendChannel<String>?
//
////    class UnstartedServerRun : ServerRun() {
////        override val start: Instant? = null
////        override val stop: Instant? = null
////        override val isRunning: Boolean = false
////        override val input: SendChannel<String>? = null
////        override val output: Flow<ServerOutput>? = null
////    }
//
////    class CompletedServerRun : ServerRun() {
////        override val start: Instant
////            get() = TODO("Not yet implemented")
////        override val stop: Instant
////            get() = TODO("Not yet implemented")
////        override val isRunning: Boolean = false
////        override val input: Nothing? = null
////        override val output: Flow<ServerOutput>
////            get() = TODO("Not yet implemented")
////    }
//
//}
//
//interface ServerRunningManager {
//    fun run(server: MinecraftServerStorage): ServerRunner
//
//    companion object {
////        val koinModule = module {
////            single<ServerRunFactory> { ServerRunFactoryImpl() }
////        }
//    }
//}
//
//interface ServerRunner {
//    val server: MinecraftServerStorage
//
//    var isRunning: Boolean
//    val output: Flow<ServerOutput>?
//    val input: SendChannel<String>?
//
////    companion object {
////        val defaultType: KClass<out ServerRun> = ServerRunFactoryImpl.ServerRunImpl::class
////    }
//}
//
//sealed interface ServerOutput
//
//@JvmInline
//value class TextOutput(val text: String) : ServerOutput
//object OutputEnd : ServerOutput
//
////private class ServerRunImpl(override val server: MinecraftServer) : ServerRun {
////
////}