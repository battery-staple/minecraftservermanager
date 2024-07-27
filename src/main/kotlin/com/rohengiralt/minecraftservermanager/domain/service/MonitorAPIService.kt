package com.rohengiralt.minecraftservermanager.domain.service

import com.google.common.hash.Hashing
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.util.*

/**
 * Contains all domain actions accessible by endpoints to the monitor microservice
 */
interface MonitorAPIService {
    fun getJar(containerUUID: UUID): File

    /**
     * Gets the sha1 hash of the minecraft jar file for a particular container.
     * @throws IOException if hashing fails
     */
    fun getSHA1(containerUUID: UUID): ByteArray
}

class MonitorAPIServiceImpl : MonitorAPIService {
    override fun getJar(containerUUID: UUID): File { // TODO: real implementation!
        return File("/minecraftservermanager/local/jars/1.8.9---RELEASE.jar")
    }

    override fun getSHA1(containerUUID: UUID): ByteArray {
        val jar = getJar(containerUUID)

        return Files.asByteSource(jar).hash(checksumHash).asBytes()
    }

    @Suppress("DEPRECATION") // SHA1 is good enough for a checksum
    private val checksumHash = Hashing.sha1()
}