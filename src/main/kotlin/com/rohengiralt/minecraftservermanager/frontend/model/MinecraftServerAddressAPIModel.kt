package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.minecraftProtocol
import io.ktor.http.*
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerAddressAPIModel(val host: String, val port: Int, val path: String, val fullAddress: String) {
    constructor(minecraftServerAddress: MinecraftServerAddress) : this(
        minecraftServerAddress.url.host,
        minecraftServerAddress.url.port,
        minecraftServerAddress.url.encodedPath,
        minecraftServerAddress.url.toString()
    )

    fun toMinecraftServerAddress(): MinecraftServerAddress =
        MinecraftServerAddress(
            url = URLBuilder(urlString = fullAddress).apply {
                protocol = URLProtocol.minecraftProtocol
            }.build()
        )
}