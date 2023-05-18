package com.rohengiralt.minecraftservermanager.domain.model

import java.util.*

data class MinecraftServer(
    val uuid: UUID,
    var name: String,
    val version: MinecraftVersion,
    val runnerUUID: UUID,
    // TODO: something representing its data/content dir (how???) ||| this may no longer be necessary now that runner is a property of the server
)