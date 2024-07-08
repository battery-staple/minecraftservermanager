package com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import org.slf4j.LoggerFactory
import java.nio.file.Path

class RedundantFallbackAPI : MinecraftJarAPI {
    private val apis: Sequence<MinecraftJarAPI> = sequence {
        logger.debug("Using launcher API")
        yield(LauncherAPI())
        logger.debug("Falling back to AWS API")
        yield(AWSAPI())
        logger.debug("Falling back to BetaCraft API")
        yield(BetaCraftAPI())
    }

    override suspend fun appendServerToPath(path: Path, version: MinecraftVersion): Boolean =
        apis
            .any { api -> api.appendServerToPath(path, version) }

    private val logger = LoggerFactory.getLogger(this::class.java)
}