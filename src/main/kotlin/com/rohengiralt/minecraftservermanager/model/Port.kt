package com.rohengiralt.minecraftservermanager.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Port(private val number: UShort) {
    init {
        require(number in numberRange)
    }

    companion object {
        val numberRange = 1001U..65535U //TODO: Configurable
        val defaultMinecraftServerPort = Port(25565U)
    }
}
