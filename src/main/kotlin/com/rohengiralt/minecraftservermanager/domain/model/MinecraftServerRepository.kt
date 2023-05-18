package com.rohengiralt.minecraftservermanager.domain.model

import java.util.*

interface MinecraftServerRepository {
    fun getServer(uuid: UUID): MinecraftServer?
    fun getAllServers(): List<MinecraftServer>

    fun addServer(minecraftServer: MinecraftServer): Boolean
    fun saveServer(minecraftServer: MinecraftServer): Boolean

    fun removeServer(uuid: UUID): Boolean
}