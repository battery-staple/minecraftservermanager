package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion
import java.nio.file.Path
import java.util.*

data class MinecraftServerJar(val uuid: UUID, val path: Path, val version: MinecraftVersion)