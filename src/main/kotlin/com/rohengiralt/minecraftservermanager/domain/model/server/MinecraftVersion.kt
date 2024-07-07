package com.rohengiralt.minecraftservermanager.domain.model.server

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion.VersionType.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//TODO: Unit Test
@Serializable
@SerialName("minecraftVersion")
sealed class MinecraftVersion {
    @Serializable
    @SerialName("vanilla")
    sealed class Vanilla : MinecraftVersion() {
        @Serializable
        @SerialName("classic")
        data class Classic(
            val majorVersion: UInt,
            val minorVersion: UInt? = null
        ) : Vanilla() {
            override val versionString get(): String =
                buildString {
                    append("1.${majorVersion}")
                    if (minorVersion != null) {
                        append(".$minorVersion")
                    }
                }

            companion object {
                private val pattern = """1.(\d+)(?:.(\d+))?""".toRegex()
                fun fromString(string: String): Classic? =
                    pattern.matchEntire(string)?.groupValues?.let { groups ->
                        val majorVersion = groups[1]
                        val minorVersion = groups[2].ifEmpty { null }
                        Classic(
                            majorVersion.toUIntOrNull() ?: return null,
                            minorVersion?.let { it.toUIntOrNull() ?: return null }
                        )
                    }
            }
        }

        @Serializable
        @SerialName("indev")
        data object Indev : Vanilla() {
            override val versionString: String = "Indev"

            fun fromString(string: String): Indev? =
                if (string == "Indev") this else null
        }

        @Serializable
        @SerialName("infdev")
        data object Infdev : Vanilla() {
            override val versionString = "Infdev"
            fun fromString(string: String): Infdev? =
                if (string == "Infdev") this else null
        }

        @Serializable
        @SerialName("alpha")
        data class Alpha(
            val phase: UInt,
            val major: UInt,
            val minor: UInt,
            val patch: UInt? = null
        ) : Vanilla() {
            init {
                require(phase == 0u) { "Invalid phase" }
                require(major > 0u) { "Invalid major" }
            }

            override val versionString get(): String =
                buildString {
                    append("$phase.$major.$minor")
                    if (patch != null) append("_$patch")
                }

            companion object {
                private val pattern = """0.(\d+).(\d+)(?:_(\d+))?""".toRegex()
                fun fromString(string: String): Alpha? =
                    pattern.matchEntire(string)?.groupValues?.let { groups ->
                        val major = groups[1]
                        val minor = groups[2]
                        val patch = groups[3].ifEmpty { null }

                        Alpha(
                            0u,
                            major.toUIntOrNull() ?: return null,
                            minor.toUIntOrNull() ?: return null,
                            patch?.let { it.toUIntOrNull() ?: return null }
                        )
                    }
            }
        }

        @Serializable
        @SerialName("beta")
        data class Beta(
            val major: UInt,
            val minor: UInt,
            val patch: UInt? = null,
            val underscoresBeforePatch: Boolean
        ) : Vanilla() {
            override val versionString get(): String =
                buildString {
                    append("$major.$minor")
                    if (patch != null) append(
                        if(underscoresBeforePatch) {
                            "_0$patch"
                        } else {
                            ".$patch"
                        }
                    )
                }

            companion object {
                private val pattern = """(\d+).(\d+)(?:(_0\d+)|(.\d+))?""".toRegex()
                fun fromString(string: String): Beta? =
                    pattern.matchEntire(string)?.groupValues?.let { groups ->
                        val major = groups[1]
                        val minor = groups[2]
                        var patch = groups[3].ifEmpty { null }
                        val underscoresBeforePatch = patch?.startsWith("_0") ?: false
                        patch = patch?.removePrefix("_0")?.removePrefix(".")

                        Beta(
                            major.toUIntOrNull() ?: return null,
                            minor.toUIntOrNull() ?: return null,
                            patch?.let { it.toUIntOrNull() ?: return null },
                            underscoresBeforePatch
                        )
                    }
            }
        }

        @Serializable
        @SerialName("release")
        data class Release(
            val phase: UInt,
            val major: UInt,
            val minor: UInt
        ) : Vanilla() {
            override val versionString get(): String = buildString {
                append("$phase.$major")
                if (minor != 0u || major == 0u) {
                    append(".$minor")
                }
            }

            companion object {
                private val pattern = """(\d+).(\d+)(?:.(\d+))?""".toRegex()
                fun fromString(string: String): Release? =
                    pattern.matchEntire(string)?.groupValues?.let { groups ->
                        val phase = groups[1]
                        val major = groups[2]
                        val minor = groups[3].ifEmpty { null }

                        Release(
                            phase.toUIntOrNull() ?: return null,
                            major.toUIntOrNull() ?: return null,
                            minor?.let { it.toUIntOrNull() ?: return null } ?: 0u,
                        )
                    }
            }
        }

        @Serializable
        @SerialName("snapshot")
        data class Snapshot(
            val year: String,
            val week: String,
            val letter: Char
        ) : Vanilla() {
            init {
                require(year.length == 2 && year.all(Char::isDigit))
                require(week.length == 2 && week.all(Char::isDigit))
            }

            override val versionString get(): String =
                "${year}w${week}${letter}"

            companion object {
                private val pattern = """(\d+)w(\d+)([a-zA-Z])""".toRegex()
                fun fromString(string: String): Snapshot? =
                    pattern.matchEntire(string)?.destructured?.let { (year, week, letter) ->
                        Snapshot(year, week, letter.singleOrNull() ?: return@let null)
                    }
            }
        }
    }

    @Serializable
    @SerialName("forge")
    sealed class Forge : MinecraftVersion() //TODO: Implement

    @Serializable
    @SerialName("custom")
    data class Custom(val string: String) : MinecraftVersion() {
        override val versionString: String = string
        companion object {
            fun fromString(string: String): Custom = Custom(string)
        }

    }

    abstract val versionString: String

    enum class VersionType {
        CLASSIC,
        INDEV,
        INFDEV,
        ALPHA,
        BETA,
        RELEASE,
        SNAPSHOT,
//        FORGE,
        CUSTOM
    }

    companion object {
        fun fromString(string: String, type: VersionType): MinecraftVersion? =
            when(type) {
                CLASSIC -> Vanilla.Classic.fromString(string)
                INDEV -> Vanilla.Indev.fromString(string)
                INFDEV -> Vanilla.Infdev.fromString(string)
                ALPHA -> Vanilla.Alpha.fromString(string)
                BETA -> Vanilla.Beta.fromString(string)
                RELEASE -> Vanilla.Release.fromString(string)
                SNAPSHOT -> Vanilla.Snapshot.fromString(string)
//                FORGE -> TODO()
                CUSTOM -> Custom.fromString(string)
            }
    }
}

val MinecraftVersion.versionType: MinecraftVersion.VersionType
    get() = when (this) {
        is MinecraftVersion.Vanilla.Alpha -> ALPHA
        is MinecraftVersion.Vanilla.Beta -> BETA
        is MinecraftVersion.Vanilla.Classic -> CLASSIC
        MinecraftVersion.Vanilla.Indev -> INDEV
        MinecraftVersion.Vanilla.Infdev -> INFDEV
        is MinecraftVersion.Vanilla.Release -> RELEASE
        is MinecraftVersion.Vanilla.Snapshot -> SNAPSHOT
        is MinecraftVersion.Custom -> CUSTOM
    }