package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftServer
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class LocalMinecraftServerContentDirectoryPathFactory(private val contentDirectoryDirectoryString: String) {
    fun newContentDirectoryPath(server: MinecraftServer): Path? =
        try {
            (contentDirectoryDirectory / server.contentDirectoryName())
                .createDirectories()
        } catch (e: IOException) {
            null
        }

    private fun MinecraftServer.contentDirectoryName(): String =
        uuid.toString()

    companion object {
        private val contentDirectoryDirectory = Path("/minecraftservermanager/local/servers")
    }
}