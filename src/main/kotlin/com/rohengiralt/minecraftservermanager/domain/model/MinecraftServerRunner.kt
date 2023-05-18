package com.rohengiralt.minecraftservermanager.domain.model

import java.util.*

/**
 * Represents a system that can run a Minecraft Server, such as a VM or container.
 */
interface MinecraftServerRunner { // TODO: How to preserve state when switching runners? (Content directory, etc.)
    val uuid: UUID
    var name: String
    val domain: String

    suspend fun runServer(
        server: MinecraftServer,
        environmentOverrides: MinecraftServerEnvironment = MinecraftServerEnvironment.EMPTY
    ): MinecraftServerCurrentRun?
    suspend fun stopRun(uuid: UUID): Boolean
    fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun?
    fun getAllCurrentRuns(server: MinecraftServer? = null): List<MinecraftServerCurrentRun>

//    fun addEnvironment(serverRun: E)
//    fun getAllEnvironments(): Sequence<E>
}