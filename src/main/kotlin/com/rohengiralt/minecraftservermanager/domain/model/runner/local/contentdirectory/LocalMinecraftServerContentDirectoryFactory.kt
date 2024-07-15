package com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory

import org.jetbrains.annotations.Contract
import java.io.IOException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div

class LocalMinecraftServerContentDirectoryFactory(contentDirectorySuperDirectoryString: String) {
    fun newContentDirectoryPath(serverUUID: UUID): Path? =
        try {
            (contentDirectorySuperDirectory / contentDirectoryName(serverUUID))
                .createDirectories()
        } catch (e: IOException) {
            null
        }

    @Contract(pure = true)
    private fun contentDirectoryName(serverUUID: UUID): String =
        serverUUID.toString()

    private val contentDirectorySuperDirectory = Path(contentDirectorySuperDirectoryString)
}