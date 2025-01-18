package com.rohengiralt.minecraftservermanager.domain.model.server

import com.rohengiralt.minecraftservermanager.util.url.URLSerializer
import io.ktor.http.*
import io.ktor.http.encodedPath
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class MinecraftServerAddress(val url: @Serializable(with= URLSerializer::class) Url) {
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