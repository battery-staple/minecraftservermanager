package com.rohengiralt.minecraftservermanager.domain.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class Port(val number: UShort)