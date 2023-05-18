package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion

interface MinecraftServerJarRepository {
    fun getJar(version: MinecraftVersion): MinecraftServerJar?
    fun saveJar(jar: MinecraftServerJar): MinecraftServerJar?
    fun deleteJar(version: MinecraftVersion): Boolean
}