package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import java.util.*

interface MinecraftServerJarResourceManager {
    suspend fun prepareJar(version: MinecraftVersion, accessorKey: UUID): Boolean
    suspend fun accessJar(version: MinecraftVersion, accessorKey: UUID): MinecraftServerJar?
    suspend fun freeJar(version: MinecraftVersion, accessorKey: UUID): Boolean
}

suspend fun MinecraftServerJarResourceManager.prepareJar(server: MinecraftServer) =
    prepareJar(server.version, server.uuid)

suspend fun MinecraftServerJarResourceManager.accessJar(server: MinecraftServer) =
    accessJar(server.version, server.uuid)

suspend fun MinecraftServerJarResourceManager.freeJar(server: MinecraftServer) =
    freeJar(server.version, server.uuid)