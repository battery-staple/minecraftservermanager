package com.rohengiralt.minecraftservermanager.domain.model.local

import com.rohengiralt.minecraftservermanager.domain.model.MinecraftVersion

class CachingMinecraftServerJarFactory : MinecraftServerJarFactory {
    override suspend fun newJar(version: MinecraftVersion): MinecraftServerJar =
        cache.getJar(version) ?: kotlin.run { // TODO: handle cache expiration
            val newJar = apiFactory.newJar(version)
            cache.saveJar(newJar) ?: newJar
        }

    private val apiFactory: APIMinecraftServerJarFactory = APIMinecraftServerJarFactory()
//    private val apiFactory: APIMinecraftServerJarFactory by lazy(::APIMinecraftServerJarFactory)
    private val cache = LocalFilesystemMinecraftServerJarRepository("cache") //TODO: Less concrete?
}