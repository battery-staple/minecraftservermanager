package com.rohengiralt.minecraftservermanager.domain.model.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import java.util.*

interface MinecraftServerJarResourceManager {
    suspend fun prepareJar(version: MinecraftVersion): Boolean
    suspend fun accessJar(version: MinecraftVersion, accessorKey: UUID): MinecraftServerJar?
    suspend fun freeJar(version: MinecraftVersion, accessorKey: UUID): Boolean
}