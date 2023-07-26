package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import kotlinx.coroutines.flow.Flow
import java.util.*

interface MinecraftServerRepository {
    fun getServer(uuid: UUID): MinecraftServer?
    fun getAllServers(): List<MinecraftServer>

    fun addServer(minecraftServer: MinecraftServer): Boolean
    fun saveServer(minecraftServer: MinecraftServer): Boolean

    fun removeServer(uuid: UUID): Boolean

    fun getServerUpdates(uuid: UUID): Flow<MinecraftServer?> // TODO: StateFlow
}