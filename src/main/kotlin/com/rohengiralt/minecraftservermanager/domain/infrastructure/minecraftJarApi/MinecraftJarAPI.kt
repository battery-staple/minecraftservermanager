package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import java.nio.file.Path

interface MinecraftJarAPI {
    suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean
}