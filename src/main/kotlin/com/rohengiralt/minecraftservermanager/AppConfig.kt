package com.rohengiralt.minecraftservermanager

import java.io.IOException
import java.util.*

const val PROPERTIES_DIR = "/app.properties"

private data class AppConfig(val debugMode: Boolean)

private fun loadAppConfig(): AppConfig {
    try {
        object {}.javaClass.getResourceAsStream(PROPERTIES_DIR)
            .use { propsStream ->
                propsStream ?: throw IOException("Could not load app config from $PROPERTIES_DIR")
                val props = Properties()
                props.load(propsStream)

                return AppConfig(
                    debugMode = props.getProperty("debug")?.toBoolean() ?: false
                )
            }
    } catch (e: IOException) {
        throw IllegalStateException("Failed to load properties file.", e)
    }
}

private val appConfig = loadAppConfig()

val debugMode: Boolean by appConfig::debugMode