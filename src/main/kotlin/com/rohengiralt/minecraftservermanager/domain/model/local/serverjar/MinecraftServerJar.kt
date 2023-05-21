package com.rohengiralt.minecraftservermanager.domain.model.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import java.nio.file.Path

data class MinecraftServerJar(val path: Path, val version: MinecraftVersion)