package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import java.nio.file.Path

interface MinecraftJarAPI {
    suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean
//    suspend fun appendClientToPath(path: Path, version: MinecraftVersion): Boolean
}