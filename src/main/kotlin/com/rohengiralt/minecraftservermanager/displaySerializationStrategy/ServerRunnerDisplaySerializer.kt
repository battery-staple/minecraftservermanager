//package com.rohengiralt.minecraftservermanager.displaySerializationStrategy
//
//import com.rohengiralt.minecraftservermanager.ServerRunner
//import com.rohengiralt.minecraftservermanager.server.MinecraftServerStorage
//import kotlinx.datetime.Instant
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.SerializationStrategy
//import kotlinx.serialization.descriptors.SerialDescriptor
//import kotlinx.serialization.descriptors.buildClassSerialDescriptor
//import kotlinx.serialization.descriptors.element
//import kotlinx.serialization.encoding.Encoder
//import kotlinx.serialization.encoding.encodeStructure
//
//@Serializable
//sealed class ServerRunDisplay {
//    abstract val start: Instant
//    abstract val stop: Instant?
//    data class CompletedServerRun(override val start: Instant, override val stop: Instant) : ServerRunDisplay()
//    data class UnfinishedServerRun(override val start: Instant) : ServerRunDisplay() {
//        override val stop: Nothing? = null
//    }
//}
//
//@Serializable
//data class ServerRunnerDisplay(val server: MinecraftServerDisplay, val isRunning: Boolean, val links: ServerRunnerEndpoints) {
//    constructor(runner: ServerRunner) : this(TODO("removed") /*MinecraftServerDisplay(runner.server)*/, runner.isRunning, ServerRunnerEndpoints(runner))
//}
//
//@Serializable
//data class ServerRunnerEndpoints(val console: String, val start: String, val stop: String) {
//    constructor(runner: ServerRunner) : this("/api/v1/servers/${runner.server.uuid}/process/console", "/api/v1/servers/${runner.server.uuid}/process/start", "/api/v1/servers/${runner.server.uuid}/process/stop")
//}
//
//object ServerRunnerDisplaySerializer: SerializationStrategy<ServerRunner> {
//    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ServerRunner") {
//        element<MinecraftServerStorage>("server")
//        element<Boolean>("isRunning")
//    }
//
//    override fun serialize(encoder: Encoder, value: ServerRunner) {
//        encoder.encodeStructure(descriptor) {
//            encodeSerializableElement(descriptor, 0, MinecraftServerStorage.serializer(), value.server)
//            encodeBooleanElement(descriptor, 1, value.isRunning)
//        }
//    }
//
//}