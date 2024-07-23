package com.rohengiralt.minecraftservermanager.domain.service

import java.io.File
import java.util.*

/**
 * Contains all domain actions accessible by endpoints to the monitor microservice
 */
interface MonitorAPIService {
    fun getJar(containerUUID: UUID): File
}

class MonitorAPIServiceImpl : MonitorAPIService {
    override fun getJar(containerUUID: UUID): File { // TODO: real implementation!
        return File("/minecraftservermanager/local/jars/1.8.9---RELEASE.jar")
    }
}