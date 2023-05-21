package com.rohengiralt.minecraftservermanager.domain.model.local.contentdirectory

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServer
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class LocalMinecraftServerContentDirectoryFactory(contentDirectorySuperDirectoryString: String) {
    fun newContentDirectoryPath(server: MinecraftServer): Path? =
        try {
            (contentDirectorySuperDirectory / server.contentDirectoryName)
                .createDirectories()
        } catch (e: IOException) {
            null
        }

    private val MinecraftServer.contentDirectoryName get(): String =
        uuid.toString()

    private val contentDirectorySuperDirectory = Path(contentDirectorySuperDirectoryString)
}