package com.rohengiralt.minecraftservermanager.domain.model.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion

interface MinecraftServerJarFactory {
    suspend fun newJar(version: MinecraftVersion): MinecraftServerJar //TODO: Should only show support for vanilla?
}