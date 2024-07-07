package com.rohengiralt.monitor

val minSpaceMb = System.getenv("minSpaceMB")?.toUIntOrNull() ?: error("minSpaceMB must be specified")
val maxSpaceMb = System.getenv("maxSpaceMB")?.toUIntOrNull() ?: error("maxSpaceMB must be specified")
val name = System.getenv("name") ?: error("name must be specified")
val port = System.getenv("port")?.toIntOrNull() ?: 8080