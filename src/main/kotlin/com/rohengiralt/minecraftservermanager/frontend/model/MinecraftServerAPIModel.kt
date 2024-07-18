package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.versionType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MinecraftServerAPIModel(
    val uuid: ServerUUID? = null,
    val name: String? = null,
    val versionPhase: MinecraftVersion.VersionType?,
    @SerialName("version") val versionString: String?,
    val runnerUUID: RunnerUUID?,
    val creationTime: Instant? = null,
) {
    constructor(minecraftServer: MinecraftServer) : this(
        uuid = minecraftServer.uuid,
        name = minecraftServer.name,
        versionPhase = minecraftServer.version.versionType,
        versionString = minecraftServer.version.versionString,
        runnerUUID = minecraftServer.runnerUUID,
        creationTime = minecraftServer.creationTime
    )

    val version: MinecraftVersion? get() =
        if (versionPhase != null && versionString != null)
            MinecraftVersion.fromString(versionString, versionPhase)
        else null

    fun toMinecraftServer(): MinecraftServer? {
        return if (uuid != null && name != null && runnerUUID != null && creationTime != null) {
            MinecraftServer(
                uuid = uuid,
                name = name,
                version = version ?: return null,
                runnerUUID = runnerUUID,
                creationTime = creationTime
            )
        } else null
    }
}