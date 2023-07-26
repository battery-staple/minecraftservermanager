package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion

interface MinecraftServerJarFactory {
    suspend fun newJar(version: MinecraftVersion): MinecraftServerJar //TODO: Should only show support for vanilla?
}