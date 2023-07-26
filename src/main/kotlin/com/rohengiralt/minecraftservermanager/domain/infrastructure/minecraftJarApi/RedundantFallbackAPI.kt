package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import java.nio.file.Path

class RedundantFallbackAPI : MinecraftJarAPI {
    private val apis: Sequence<MinecraftJarAPI> = sequence {
        yield(LauncherAPI())
        yield(AWSAPI())
    }

    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean =
        apis
            .any { api -> api.appendServerToPath(path, version) }
}