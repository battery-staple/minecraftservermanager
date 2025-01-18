package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Represents a token used to authenticate against a monitor instance
 */
@JvmInline
@OptIn(ExperimentalEncodingApi::class)
value class MonitorToken(val bytes: ByteArray) {
    fun asString() = Base64.encode(bytes)

    override fun toString(): String = asString()

    companion object {
        fun fromString(token: String): MonitorToken {
            return MonitorToken(Base64.decode(token))
        }
    }
}