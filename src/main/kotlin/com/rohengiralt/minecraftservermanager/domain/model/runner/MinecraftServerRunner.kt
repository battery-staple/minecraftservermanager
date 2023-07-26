package com.rohengiralt.minecraftservermanager.domain.model.runner

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerEnvironment
import kotlinx.coroutines.flow.Flow
import java.util.*

/**
 * Represents a system that can run a Minecraft Server, such as a VM or container.
 */
interface MinecraftServerRunner {
    val uuid: UUID
    var name: String
    val domain: String

    suspend fun initializeServer(server: MinecraftServer): Boolean
    suspend fun removeServer(server: MinecraftServer): Boolean
    suspend fun runServer(
        server: MinecraftServer,
        environmentOverrides: MinecraftServerEnvironment = MinecraftServerEnvironment.EMPTY
    ): MinecraftServerCurrentRun?
    suspend fun stopRun(uuid: UUID): Boolean
    suspend fun stopRunByServer(serverUUID: UUID): Boolean
    suspend fun stopAllRuns(): Boolean
    fun getCurrentRun(uuid: UUID): MinecraftServerCurrentRun?
    fun getAllCurrentRuns(): List<MinecraftServerCurrentRun>

    fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun?
    fun isRunning(serverUUID: UUID): Boolean = getCurrentRunByServer(serverUUID) != null

    suspend fun getAllCurrentRunsFlow(server: MinecraftServer? = null): Flow<List<MinecraftServerCurrentRun>>

//    fun addEnvironment(serverRun: E)
//    fun getAllEnvironments(): Sequence<E>
}