package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion

interface MinecraftServerJarFactory {
    /**
     * Creates a new Minecraft server jar for the version [version].
     * @return a new jar, or null if one could not be found.
     */
    suspend fun newJar(version: MinecraftVersion): MinecraftServerJar? //TODO: Should only show support for vanilla?
}