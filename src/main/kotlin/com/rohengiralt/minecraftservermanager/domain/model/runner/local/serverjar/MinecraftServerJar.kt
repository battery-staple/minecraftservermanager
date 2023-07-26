package com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import java.nio.file.Path

data class MinecraftServerJar(val path: Path, val version: MinecraftVersion)