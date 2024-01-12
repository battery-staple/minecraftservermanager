package com.rohengiralt.minecraftservermanager

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec

private val hostConfig = Config { addSpec(HostSpec) }
    .from.env()

private object HostSpec : ConfigSpec() {
    val hostname by required<String>()
}

val hostname by lazy { hostConfig[HostSpec.hostname] }