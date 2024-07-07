package com.rohengiralt.monitor

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