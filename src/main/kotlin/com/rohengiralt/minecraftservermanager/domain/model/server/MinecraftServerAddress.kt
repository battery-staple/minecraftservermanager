package com.rohengiralt.minecraftservermanager.domain.model.server

import io.ktor.http.*

@JvmInline
value class MinecraftServerAddress(val url: Url) {
    init {
        require(url.protocol == URLProtocol.minecraftProtocol)
    }

    constructor(host: String, port: Port? = null, path: String? = null) : this(
        URLBuilder().apply {
            protocol = URLProtocol.minecraftProtocol
            this.host = host
            port?.let { this.port = it.number.toInt() }
            path?.let { encodedPath = it }
        }.build()
    )
}

val URLProtocol.Companion.minecraftProtocol: URLProtocol by lazy { URLProtocol("mc", defaultPort = 25565) }