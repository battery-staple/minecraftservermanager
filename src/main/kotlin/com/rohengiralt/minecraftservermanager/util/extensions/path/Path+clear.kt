package com.rohengiralt.minecraftservermanager.util.extensions.path

import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists

fun Path.clear() {
    deleteIfExists()
    createFile()
}