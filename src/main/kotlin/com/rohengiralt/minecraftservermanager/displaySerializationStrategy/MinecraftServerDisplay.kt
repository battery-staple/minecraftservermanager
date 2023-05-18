//package com.rohengiralt.minecraftservermanager.displaySerializationStrategy
//
//import com.rohengiralt.minecraftservermanager.ServerRunner
//import com.rohengiralt.minecraftservermanager.model.Port
//import com.rohengiralt.minecraftservermanager.server.MinecraftServer
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerRun
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerRuns
//import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
//import kotlinx.serialization.Serializable
//import java.util.*
//
//@Serializable
//data class ServerDisplay(
//    @Serializable(with = UUIDSerializer::class) val id: UUID? = null,
//    val name: String? = null,
//    val version: String? = null,
//    val port: Port? = null,
//    val isRunning: Boolean? = null,
//    val links: ServerLinks
//)
//
//@Serializable
//data class ServerLinks(val runs: String) {
//    constructor(
//        runner: ServerRunner
//    ) : this(
//        "/api/v1/rest/servers/${runner.server.uuid}/runs"
//    )
//}
//
////@Serializable
////data class ServerLinks(val console: String, val start: String, val stop: String) {
////    constructor(runner: ServerRunner) : this("/api/v1/servers/${runner.server.uuid}/process/console", "/api/v1/servers/${runner.server.uuid}/process/start", "/api/v1/servers/${runner.server.uuid}/process/stop")
////}
//
//@Serializable
//data class MinecraftServerDisplay(
//    @Serializable(with = UUIDSerializer::class) val id: UUID? = null,
//    val name: String? = null,
//    val version: String? = null,
//    val port: Port? = null,
//) {
//    constructor(server: MinecraftServer) : this(
//        id = server.uuid,
//        name = server.name,
//        version = server.version,
//        port = server.port
//    )
//}
//
//data class MinecraftServerRunsDisplay(
//    val past: List<ArchivedServerRunDisplay>,
//    val current: List<RunningServerRunDisplay>
//) {
//    constructor(serverRuns: MinecraftServerRuns) : this(
//        past = serverRuns.past.map(::ArchivedServerRunDisplay),
//        current = serverRuns.current.map(::RunningServerRunDisplay)
//    )
//}
//
//data class ArchivedServerRunDisplay(
//    val uuid: UUID,
//    val startTime: String,
//    val endTime: String
//) {
//    constructor(serverRun: MinecraftServerRun.Archived) : this(
//        uuid = serverRun.uuid,
//        startTime = serverRun.startTime.toString(),
//        endTime = serverRun.endTime.toString()
//    )
//}
//
//data class RunningServerRunDisplay(
//    val uuid: UUID,
//    val startTime: String,
//) {
//    constructor(serverRun: MinecraftServerRun.Running) :
//            this(
//                uuid = serverRun.uuid,
//                startTime = serverRun.startTime.toString()
//            )
//}