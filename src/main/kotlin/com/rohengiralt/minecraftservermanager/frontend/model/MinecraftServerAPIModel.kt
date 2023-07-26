package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.server.versionType
import com.rohengiralt.minecraftservermanager.util.extensions.uuid.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class MinecraftServerAPIModel(
    @Serializable(with = UUIDSerializer::class) val uuid: UUID? = null,
    val name: String? = null,
    val versionPhase: MinecraftVersion.VersionType?,
    @SerialName("version") val versionString: String?,
    @Serializable(with = UUIDSerializer::class) val runnerUUID: UUID?,
) {
    constructor(minecraftServer: MinecraftServer) : this(
        uuid = minecraftServer.uuid,
        name = minecraftServer.name,
        versionPhase = minecraftServer.version.versionType,
        versionString = minecraftServer.version.versionString,
        runnerUUID = minecraftServer.runnerUUID
    )

    val version: MinecraftVersion? get() =
        if (versionPhase != null && versionString != null)
            MinecraftVersion.fromString(versionString, versionPhase)
        else null

    fun toMinecraftServer(): MinecraftServer? {
        return if (uuid != null && name != null && runnerUUID != null) {
            MinecraftServer(
                uuid = uuid,
                name = name,
                version = version ?: return null,
                runnerUUID = runnerUUID,
            )
        } else null
    }
}