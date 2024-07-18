package com.rohengiralt.minecraftservermanager.domain

import java.util.*

/**
 * A unique identifier for a domain object, such as a Minecraft Server.
 */
interface ResourceUUID {
    val value: UUID
}