package com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory

import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import org.jetbrains.annotations.Contract
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class LocalMinecraftServerContentDirectoryFactory(contentDirectorySuperDirectoryString: String) {
    fun newContentDirectoryPath(serverUUID: ServerUUID): Path? =
        try {
            (contentDirectorySuperDirectory / contentDirectoryName(serverUUID))
                .createDirectories()
        } catch (e: IOException) {
            null
        }

    @Contract(pure = true)
    private fun contentDirectoryName(serverUUID: ServerUUID): String =
        serverUUID.value.toString()

    private val contentDirectorySuperDirectory = Path(contentDirectorySuperDirectoryString)
}