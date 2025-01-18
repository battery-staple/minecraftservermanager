package com.rohengiralt.monitor

import java.nio.file.Paths
import kotlin.io.path.div

/**
 * The minimum space (in MB) to allocate to the Minecraft server
 */
val minSpaceMB = System.getenv("minSpaceMB")?.toUIntOrNull() ?: error("minSpaceMB must be specified")

/**
 * The maximum space (in MB) to allocate to the Minecraft server
 */
val maxSpaceMB = System.getenv("maxSpaceMB")?.toUIntOrNull() ?: error("maxSpaceMB must be specified")

/**
 * The name of the Minecraft server
 */
val name = System.getenv("name") ?: error("name must be specified")

/**
 * The port on which to run this application (not the Minecraft server)
 */
val port = System.getenv("port")?.toIntOrNull() ?: 8080

/**
 * The unique secret token that can be used to authenticate against this application
 */
val token = System.getenv("token")

/**
 * The directory containing all of this app's data
 */
val dataDir = Paths.get("/monitor")

/**
 * The path to the jar that is run
 */
val jarPath = dataDir / "minecraftserver.jar"

/**
 * Where the minecraft server's data is stored
 */
val rundataPath = dataDir / "rundata"
