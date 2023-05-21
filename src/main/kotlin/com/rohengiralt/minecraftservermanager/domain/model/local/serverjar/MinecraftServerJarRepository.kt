package com.rohengiralt.minecraftservermanager.domain.model.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion

interface MinecraftServerJarRepository {
    suspend fun getJar(version: MinecraftVersion): MinecraftServerJar?
    suspend fun deleteJar(version: MinecraftVersion): Boolean
}