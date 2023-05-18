//package com.rohengiralt.minecraftservermanager.server
//
//import com.rohengiralt.minecraftservermanager.ServerProcessFactory
//import com.rohengiralt.minecraftservermanager.dataStructure.observableCollection.ObservableMutableList
//import com.rohengiralt.minecraftservermanager.model.MinecraftServerRuntimeConfigurationStorage
//import com.rohengiralt.minecraftservermanager.model.Port
//import com.rohengiralt.minecraftservermanager.observation.Observable
//import com.rohengiralt.minecraftservermanager.observation.PassthroughPublisher
//import com.rohengiralt.minecraftservermanager.observation.SimplePublisher
//import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.channels.SendChannel
//import kotlinx.coroutines.flow.*
//import kotlinx.datetime.Clock
//import kotlinx.datetime.Instant
//import kotlinx.serialization.Serializable
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.inject
//import java.io.IOException
//import java.nio.file.Path
//import java.util.*
//
//class MinecraftServer private constructor(
//    uuid: UUID,
//    name: String,
//    version: String,
//    port: Port,
//    runtimeConfiguration: MinecraftServerRuntimeConfiguration,
//    val runs: MinecraftServerRuns,
//    private val publisher: PassthroughPublisher
//) : Observable by publisher {
//    constructor(
//        uuid: UUID,
//        name: String,
//        version: String,
//        port: Port,
//        runtimeConfiguration: MinecraftServerRuntimeConfiguration,
//        runs: MinecraftServerRuns,
//    ) : this(
//        uuid = uuid,
//        name = name,
//        version = version,
//        port = port,
//        runtimeConfiguration = runtimeConfiguration,
//        runs = runs,
//        publisher = PassthroughPublisher(SimplePublisher())
//    )
//
//    val uuid: UUID by publisher.published(uuid)
//    var name: String by publisher.published(name)
//    var version: String by publisher.published(version)
//    var port: Port by publisher.published(port)
//    val runtimeConfiguration: MinecraftServerRuntimeConfiguration by publisher.published(runtimeConfiguration)
//
//    private inner class ServerRuns private constructor(): MinecraftServerRuns, Observable by publisher { //TODO: Remove Interface
//        override val current: ObservableMutableList<Running> =
//            ObservableMutableList<Running>()
//                .apply {
//                    addWeakSubscriber(publisher)
//                }
//
//        override fun start(): Boolean {
//            TODO("Not yet implemented")
//        }
//
//        override suspend fun stop(runId: UUID): Boolean {
//            current
//                .find { it.uuid == runId }
//                ?.also { runToStop ->
//                    current.remove(runToStop)
//                    past.add(
//                        Archived(
//                            uuid = runToStop.uuid,
//                            startTime = runToStop.startTime,
//                            endTime = Clock.System.now(),
//                            log = runToStop.log,
//                        )
//                    )
//                }
//                ?.stop()
//                ?: return false
//
//            return true
//        }
//
//        override val past: ObservableMutableList<Archived> =
//            ObservableMutableList<Archived>()
//                .apply {
//                    addWeakSubscriber(publisher)
//                }
//
//        inner class Running : MinecraftServerRun.Running, KoinComponent {
//            override val uuid: UUID = UUID.randomUUID()
//            override val startTime: Instant = Clock.System.now()
//
//            override val output: Flow<MinecraftServerRun.ServerOutput>
//                get() = _outputFlow.asSharedFlow()
//            override val input: SendChannel<String> get() = _input
//
//            internal suspend fun stop() = withContext(Dispatchers.IO) {
//                process.destroy()
//                withTimeout(5000L) { //TODO: No magic number
//                    try {
//                        process.waitFor()
//                    } finally {
//                        process.destroyForcibly()
//                    }
//                }
//            }
//
//            internal val log: List<String> = mutableListOf()
//
//            private val processIOScope = CoroutineScope(Dispatchers.IO)
//            private val processFactory: ServerProcessFactory by inject()
//            private val process =
//                processFactory
//                    .getProcess(configuration = runtimeConfiguration)
//                    .also {
//                        with(processIOScope) {
//                            launch {
//                                it.inputChannelJob()
//                            }
//                            launch {
//                                it.outputChannelJob()
//                            }
//                        }
//                    }
//
//            private val _input: Channel<String> = Channel()
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
//        }
//
//        private val _outputFlow: MutableSharedFlow<MinecraftServerRun.ServerOutput> = MutableSharedFlow(Channel.UNLIMITED)
//        private suspend fun Process.outputChannelJob() {
//            coroutineScope {
//                println("Piping output")
//                launch(Dispatchers.IO) {
//                    try {
//                        (inputStream ?: return@launch println("No output stream"))
//                            .bufferedReader()
//                            .lineSequence()
//                            .asFlow() // Will this prevent blocking the thread when waiting?
//                            .flowOn(Dispatchers.IO)
//                            .collect {
//                                try {
//                                    println("New server output $it")
//                                    _outputFlow.emit(MinecraftServerRun.TextOutput(it))
//                                } catch (e: IOException) {
//                                    println("Cannot read server output")
//                                    cancel()
//                                }
//                            }
//                    } catch (e: Throwable) {
//                        println("Stream threw error $e")
//                    } finally {
//                        println("Stream ended")
//                        @OptIn(ExperimentalCoroutinesApi::class)
//                        _outputFlow.resetReplayCache()
//                        _outputFlow.emit(MinecraftServerRun.OutputEnd)
//                    }
//                }
//            }
//        }
//
//
//        inner class Archived(
//            override val uuid: UUID,
//            override val startTime: Instant,
//            override val endTime: Instant,
//            override val log: List<String>
//        ) : MinecraftServerRun.Archived {
//            override fun equals(other: Any?): Boolean {
//                if (this === other) return true
//                if (javaClass != other?.javaClass) return false
//
//                other as Archived
//
//                if (startTime != other.startTime) return false
//                if (endTime != other.endTime) return false
//                if (log != other.log) return false
//                if (uuid != other.uuid) return false
//
//                return true
//            }
//
//            override fun hashCode(): Int {
//                var result = startTime.hashCode()
//                result = 31 * result + endTime.hashCode()
//                result = 31 * result + log.hashCode()
//                result = 31 * result + uuid.hashCode()
//                return result
//            }
//
//            override fun toString(): String {
//                return "Archived(startTime=$startTime, endTime=$endTime, log=$log, uuid=$uuid)"
//            }
//        }
//     }
//}
//
//
//data class MinecraftServerRuntimeConfiguration(
//    var jar: Path,
//    var contentDirectory: Path,
//)
//
//interface MinecraftServerRuns : Observable {
//    val past: List<MinecraftServerRun.Archived>
//    val current: List<MinecraftServerRun.Running>
//
//    fun start(/*runConfiguration: RunConfiguration*/): Boolean
//    suspend fun stop(runId: UUID): Boolean // puts run into `past`
//
////    interface RunConfiguration {
////
////    }
//}
//
//sealed interface MinecraftServerRun {
//    val uuid: UUID
//    val startTime: Instant
//
//    interface Running : MinecraftServerRun {
//        val output: Flow<ServerOutput>
//        val input: SendChannel<String>
//    }
//
//    interface Archived : MinecraftServerRun {
//        val endTime: Instant
//        val log: List<String> // Should be Sequence/Flow?
//    }
//
//    sealed interface ServerOutput
//
//    @JvmInline
//    value class TextOutput(val text: String) : ServerOutput
//    object OutputEnd : ServerOutput
//}
//
//sealed interface MinecraftServerRunStorage {
//    val uuid: UUID
//    val startTime: Instant
//
//    interface Running : MinecraftServerRun
//
//    interface Archived : MinecraftServerRun {
//        val endTime: Instant
//    }
//}
//
//@Serializable
//data class MinecraftServerStorage(
//    @Serializable(with = UUIDSerializer::class) val uuid: UUID,
//    val name: String,
//    val version: String,
//    val port: Port,
//    val configuration: MinecraftServerRuntimeConfigurationStorage,
////    val runs: ServerRuns1 = ServerRuns1()
//) : KoinComponent {
//    constructor(server: MinecraftServer) :
//            this(uuid = server.uuid, name = server.name, version = server.version, port = server.port, configuration = MinecraftServerRuntimeConfigurationStorage(server.runtimeConfiguration))
//
//    fun toMinecraftServer(): MinecraftServer =
//        MinecraftServer(
//            uuid = uuid,
//            name = name,
//            version = version,
//            port = port,
//            runtimeConfiguration = MinecraftServerRuntimeConfiguration(
//                jar = configuration.jar,
//                contentDirectory = configuration.contentDirectory,
//            ),
////            runs = null,
//            runs = TODO()
//        )
//
////    companion object : KoinComponent {
////        suspend operator fun invoke(uuid: UUID, name: String, version: String, port: Port): MinecraftServer {
////            val serverJarRepository by inject<ServerJarRepository>()
////            val serverRuntimeDirectoryRepository by inject<ServerRuntimeDirectoryRepository>()
////
////            return MinecraftServer(
////                uuid,
////                name,
////                version,
////                port,
////                serverJarRepository.getJar(version),
////                MinecraftServerConfiguration(serverRuntimeDirectoryRepository.getRuntimeDirectory(uuid.toString()))
////            )
////        }
////    }
//}
//
////class ModdedMinecraftServer(name: String, version: String, jar: File) : MinecraftServer(name, version, jar)
