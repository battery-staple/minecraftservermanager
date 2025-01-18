package com.rohengiralt.minecraftservermanager.util.url

import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.parseQueryString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for [io.ktor.http.Url]
 */
object URLSerializer : KSerializer<Url> {
    override val descriptor: SerialDescriptor = UrlSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Url) {
        val surrogate = UrlSurrogate(
            protocol = value.protocol,
            host = value.host,
            port = value.port,
            encodedPath = value.encodedPath,
            encodedUser = value.encodedUser,
            encodedPassword = value.encodedPassword,
            encodedQuery = value.encodedQuery,
            encodedFragment = value.encodedFragment,
            trailingQuery = value.trailingQuery
        )
        encoder.encodeSerializableValue(UrlSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): Url {
        val surrogate = decoder.decodeSerializableValue(UrlSurrogate.serializer())
        val builder = URLBuilder().apply { // Mimicking URLBuilder.takeFrom(Url)
            protocol = surrogate.protocol
            host = surrogate.host
            port = surrogate.port
            encodedPath = surrogate.encodedPath
            encodedUser = surrogate.encodedUser
            encodedPassword = surrogate.encodedPassword
            encodedParameters = ParametersBuilder().apply { appendAll(parseQueryString(surrogate.encodedQuery, decode = false)) }
            encodedFragment = surrogate.encodedFragment
            trailingQuery = surrogate.trailingQuery
        }
        return builder.build()
    }

    @Serializable
    @SerialName("Url")
    private data class UrlSurrogate(
        @Serializable(with= URLProtocolSerializer::class) val protocol: URLProtocol,
        val host: String,
        val port: Int,
        val encodedPath: String,
        val encodedUser: String?,
        val encodedPassword: String?,
        val encodedQuery: String,
        val encodedFragment: String,
        val trailingQuery: Boolean
    )
}

/**
 * Serializer for [io.ktor.http.URLProtocol]
 */
object URLProtocolSerializer : KSerializer<URLProtocol> {
    override val descriptor: SerialDescriptor = URLProtocolSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: URLProtocol) {
        val surrogate = URLProtocolSurrogate(
            name = value.name,
            defaultPort = value.defaultPort,
        )
        encoder.encodeSerializableValue(URLProtocolSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): URLProtocol {
        val surrogate = decoder.decodeSerializableValue(URLProtocolSurrogate.serializer())
        return URLProtocol(name = surrogate.name, defaultPort = surrogate.defaultPort)
    }

    @Serializable
    @SerialName("URLProtocol")
    private data class URLProtocolSurrogate(
        val name: String,
        val defaultPort: Int,
    )
}