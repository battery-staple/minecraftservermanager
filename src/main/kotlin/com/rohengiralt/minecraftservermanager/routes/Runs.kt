//package com.rohengiralt.minecraftservermanager.routes
//
//import com.rohengiralt.minecraftservermanager.ServerRunningManager
//import com.rohengiralt.minecraftservermanager.ServerRuns1
//import com.rohengiralt.minecraftservermanager.displaySerializationStrategy.ServerRunnerDisplay
//import com.rohengiralt.minecraftservermanager.model.Port
//import com.rohengiralt.minecraftservermanager.plugins.BadRequestException
//import com.rohengiralt.minecraftservermanager.plugins.NotAllowedException
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerStorage
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerStorageRepository
//import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
//import io.ktor.application.*
//import io.ktor.features.*
//import io.ktor.http.*
//import io.ktor.response.*
//import io.ktor.routing.*
//import io.ktor.util.pipeline.*
//import io.ktor.websocket.*
//import kotlinx.serialization.Serializable
//import org.koin.ktor.ext.inject
//import java.util.*
//
//@Serializable
//data class ServerModel(
//    @Serializable(with = UUIDSerializer::class) val id: UUID? = null,
//    val name: String? = null,
//    val version: String? = null,
//    val port: Port? = null,
//    val isRunning: Boolean? = null,
//    val links: ServerLinks? = null
//)
//
//@Serializable
//data class ServerLinks(val runs: String) {
//    constructor(
//        server: MinecraftServerStorage
//    ) : this(
//        "/api/v1/rest/servers/${server.uuid}/runs"
//    )
//}
//
//fun Route.runsRoutes2() = route("/runs") {
//    val serverRepo: MinecraftServerStorageRepository by this@runsRoutes.inject()
//    get {
//        val server = serverRepo.getServer(
//            UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//        ) ?: throw BadRequestException()
//
//        call.respond(server.runs)
//    }
//
//    post {
//        val server = serverRepo.getServer(
//            UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//        ) ?: throw BadRequestException()
//
//        serverRepo.setOrAddServer(
//            server.copy(
//                runs = server.runs.copy(current = server.runs.current + ServerRuns1.ServerRun().apply { start() })
//            )
//        )
//    }
//
//    route("/current/{runId}") {
//        get {
//            val server = serverRepo.getServer(
//                UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//            ) ?: throw BadRequestException()
//            val run: ServerRuns1.ServerRun = server.runs.current.find { it.uuid == call.parameters["runId"] } ?: throw BadRequestException()
//
//            call.respond(run)
//        }
//
//        delete {
//            val server = serverRepo.getServer(
//                UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//            ) ?: throw BadRequestException()
//            val run: ServerRuns1.ServerRun = server.runs.current.find { it.uuid == call.parameters["runId"] } ?: throw BadRequestException()
//
//            run.stop()
//
//            serverRepo.setOrAddServer(
//                server.runs.copy( //TODO: CONCURRENCY—what if `server` changes??
//                    runs = server.runs.copy(
//                        current = server.runs.current - run,
//                        past = server.runs.past + run
//                    )
//                )
//            )
//
//            call.respond(HttpStatusCode.NoContent)
//        }
//
//        put {
//            throw NotAllowedException()
//        }
//
//        patch {
//            throw NotAllowedException()
//        }
//
//        post {
//            throw NotAllowedException()
//        }
//
//    }
//
//    route("/past/{runId}") {
//        get {
//
//        }
//    }
//}
//
//class ProcessRepository {
//    private val processes = mutableMapOf<UUID, Process>()
//
//    fun create(): UUID {}
//    operator fun get(uuid: UUID): Process? {
//        return processes[uuid]
//    }
//}
//val pr = ProcessRepository()
//
//data class ServerRun(val uuid: UUID) {
//    val state: RunState = RunState.Unstarted
//
//    val isRunning: Boolean
//        get() = processId?.let { id -> pr[id] } != null
//
//    fun started(): ServerRun? {
//        if (state !is RunState.Unstarted) return null
//
//        return copy(
//            state = RunState.Started(
//                pr.create()
//            )
//        )
//    }
//
//    fun stopped(): ServerRun? {
//        if (state !is RunState.Started) return null
//        pr[state.processId].destroy()
//
//        pr[processId ?: return]?.destroy()
//        processId = null
//    }
//
//    sealed class RunState {
//        object Unstarted : RunState()
//        class Started(val processId: UUID) : RunState()
//        class Finished : RunState()
//    }
//}
//
//data class ServerRuns(val current: List<ServerRun>) {
//    fun start(uuid: UUID) {
//        current
//    }
//}
//
//fun Route.runsRoutes() = route("/runs") {
//    val serverRepo: MinecraftServerStorageRepository by this@runsRoutes.inject()
//    val runManager: ServerRunningManager by this@runsRoutes.inject()
////    val runFactory: ServerRunFactory by this@runsRoutes.inject()
//    suspend fun PipelineContext<Unit, ApplicationCall>.getServerRunner() =
//        serverRepo.getServer(
//            UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//        )?.let { runManager.run(it) } ?: throw NotFoundException()
//    suspend fun WebSocketServerSession.getServerRunner() =
//        serverRepo.getServer(
//            UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//        )?.let { runManager.run(it) } ?: throw NotFoundException()
//
//    get {
//        call.respond(ServerRunnerDisplay(getServerRunner()))
//    }
//
//    post {
//        val server = serverRepo.getServer(
//            UUID.fromString(call.parameters["id"] ?: throw BadRequestException())
//        ) ?: throw NotFoundException()
//
//        serverRepo.setOrAddServer(
//            server.copy(
//                runs = server.runs.
//            )
//        )
//    }
//
////    post {
////        getServerRunner().isRunning = true
////        call.respond(HttpStatusCode.OK)
////    }
////
////    post {
////        getServerRunner().isRunning = false
////        call.respond(HttpStatusCode.OK)
////    }
//
////    webSocket("/console") {
////        coroutineScope {
////            val runningServer = getServerRunner()
////
////            println("Opening Webhook")
////            launch(Dispatchers.IO) {
////                runningServer.output?.collect {
////                    when (it) {
////                        is TextOutput -> {
////                            println("sending $it")
////                            outgoing.send(Frame.Text(it.text))
////                        }
////                        OutputEnd -> {
////                            println("Closing Webhook")
////                            return@collect close(CloseReason(CloseReason.Codes.NORMAL, "Process Ended"))
////                        }
////                    }
////                } ?: return@launch close(CloseReason(CloseReason.Codes.NORMAL, "No running process"))
////            }
////
////            launch(Dispatchers.IO) {
////                incoming.consumeEach {
////                    launch {
////                        (it as? Frame.Text)?.let { frame ->
////                            println("Received ${frame.readText()}")
////                            runningServer.input?.send(frame.readText()) ?: close(CloseReason(CloseReason.Codes.NORMAL, "No running process"))
////                        } ?: println("Received non-text frame with type ${it.frameType}")
////                    }
////                }
////            }
////        }
////    }
//}