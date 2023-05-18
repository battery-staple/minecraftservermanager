package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerAddress
import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.minecraftProtocol
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

@Serializable
data class MinecraftServerCurrentRunAPIModel(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val serverId: UUID? = null,
    @Serializable(with = UUIDSerializer::class) val runnerId: UUID? = null,
    val environment: MinecraftServerEnvironmentAPIModel? = null,
    @Serializable(with = MinecraftServerAddressSerializer::class) val address: MinecraftServerAddress? = null
) {
    constructor(run: MinecraftServerCurrentRun) : this(
        uuid = run.uuid,
        serverId = run.serverId,
        runnerId = run.runnerId,
        environment = MinecraftServerEnvironmentAPIModel(run.environment),
        address = run.address
    )
}

object MinecraftServerAddressSerializer : KSerializer<MinecraftServerAddress> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MinecraftServerAddress) {
        encoder.encodeString(value.url.toString())
    }

    override fun deserialize(decoder: Decoder): MinecraftServerAddress =
        MinecraftServerAddress(
            url = URLBuilder(urlString = decoder.decodeString()).apply {
                protocol = URLProtocol.minecraftProtocol
            }.build()
        )
}