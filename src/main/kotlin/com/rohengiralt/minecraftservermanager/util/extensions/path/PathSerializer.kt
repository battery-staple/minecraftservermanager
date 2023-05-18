package com.rohengiralt.minecraftservermanager.util.extensions.path

import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Serializer(forClass = Path::class)
object PathSerializer { // TODO: absolutePathString/Paths.get() are not necessarily opposites
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("path", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Path =
        Path(decoder.decodeString())


    override fun serialize(encoder: Encoder, value: Path) {
        encoder.encodeString(value.absolutePathString())
    }
}