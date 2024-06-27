package com.rohengiralt.minecraftservermanager.domain.model.server

import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion.VersionType.*
import com.rohengiralt.minecraftservermanager.util.ifNull
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
        @SerialName("preClassic")
        data class PreClassic(
            val gameInitials: String,
            val dayOfMonth: String,
            val hours: String,
            val minutes: String
        ) : Vanilla() {
            init {
                require(dayOfMonth.length == 2 && dayOfMonth.all(Char::isDigit)) { "Invalid day of month component" }
                require(hours.length == 2 && hours.all(Char::isDigit)) { "Invalid hour time component" }
                require(minutes.length == 2 && minutes.all(Char::isDigit)) { "Invalid minute time component" }
            }

            override val versionString get(): String =
                "$gameInitials-$dayOfMonth$hours$minutes"

            companion object {
                private val pattern = """(.*)-(\d{2})(\d{2})(\d{2})""".toRegex()
                fun fromString(string: String): PreClassic? =
                    pattern.matchEntire(string)?.destructured?.let { (gameInitials, dayOfMonth, hours, minutes) ->
                        PreClassic(gameInitials, dayOfMonth, hours, minutes)
                    }
            }
        }

        @Serializable
        @SerialName("classic")
        sealed class Classic : Vanilla() {
            @Serializable
            @SerialName("preSurvivalTest")
            data class PreSurvivalTest(
                val minorVersion: UInt,
                val patch: UInt? = null
            ) : Classic() {
                override val versionString get(): String =
                    buildString {
                        append("0.0.${minorVersion}a")
                        if (patch != null) {
                            append("_$patch")
                        }
                    }

                companion object {
                    private val pattern = """0.0.(\d+)(?:_(\d+))?""".toRegex()
                    fun fromString(string: String): PreSurvivalTest? =
                        pattern.matchEntire(string)?.groupValues?.let { groups ->
                            val minorVersion = groups[1]
                            val patch = groups[2].ifEmpty { null }
                            PreSurvivalTest(
                                minorVersion.toUIntOrNull() ?: return null,
                                patch?.let { it.toUIntOrNull() ?: return null }
                            )
                        }
                }
            }

            @Serializable
            @SerialName("survivalTest")
            data class SurvivalTest(
                val minorVersion: UInt,
                val patch: UInt? = null,
                val usesUnderscores: Boolean = false
            ) : Classic() {
                override val versionString get(): String = buildString {
                    append("0.$minorVersion")
                    append(
                        if (usesUnderscores) {
                            "_SURVIVAL_TEST"
                        } else {
                            " SURVIVAL TEST"
                        }
                    )
                    if (patch != null) {
                        append("_$patch")
                    }
                }

                companion object {
                    private val pattern = """0.(\d+)(_SURVIVAL_TEST| SURVIVAL TEST)(?:_(\d+))?""".toRegex()
                    fun fromString(string: String): SurvivalTest? =
                        pattern.matchEntire(string)?.groupValues?.let { groups ->
                            val minorVersion = groups[1]
                            val survivalTestString = groups[2]
                            val patch = groups[3].ifEmpty { null }
                            val usesUnderscores = survivalTestString == "_SURVIVAL_TEST"

                            SurvivalTest(
                                minorVersion.toUIntOrNull() ?: return null,
                                patch?.let { it.toUIntOrNull() ?: return null },
                                usesUnderscores
                            )
                        }
                }
            }

            @Serializable
            @SerialName("postSurvivalTest")
            data class PostSurvivalTest(
                val minorVersion: UInt,
                val patch: UInt? = null
            ) : Classic() {
                override val versionString get(): String =
                    buildString {
                        append("0.$minorVersion")
                        if (patch != null) {
                            append("_$patch")
                        }
                    }

                companion object {
                    private val pattern = """0.(\d+)(?:_(\d+))?""".toRegex()
                    fun fromString(string: String): PostSurvivalTest? =
                        pattern.matchEntire(string)?.groupValues?.let { groups ->
                            val minorVersion = groups[1]
                            val patch = groups[2].ifEmpty { null }

                            PostSurvivalTest(
                                minorVersion.toUIntOrNull() ?: return null,
                                patch?.let { it.toUIntOrNull() ?: return null }
                            )
                        }
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
                require(phase > 0u) { "Invalid phase" }
                require(major > 0u) { "Invalid major" }
            }

            override val versionString get(): String =
                buildString {
                    append("v$phase.$major.$minor")
                    if (patch != null) append("_$patch")
                }

            companion object {
                private val pattern = """v(\d+).(\d+).(\d+)(?:_(\d+))?""".toRegex()
                fun fromString(string: String): Alpha? =
                    pattern.matchEntire(string)?.groupValues?.let { groups ->
                        val phase = groups[1]
                        val major = groups[2]
                        val minor = groups[3]
                        val patch = groups[4].ifEmpty { null }

                        Alpha(
                            phase.toUIntOrNull() ?: return null,
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
        init {
            // TODO: require not a valid string of any other type
        }

        override val versionString: String = string
        companion object {
            fun fromString(string: String): Custom = Custom(string)
        }

    }

    abstract val versionString: String

    enum class VersionType {
        PRE_CLASSIC,
        PRE_SURVIVAL_TEST,
        SURVIVAL_TEST,
        POST_SURVIVAL_TEST,
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
                PRE_CLASSIC -> Vanilla.PreClassic.fromString(string)
                PRE_SURVIVAL_TEST -> Vanilla.Classic.PreSurvivalTest.fromString(string)
                SURVIVAL_TEST -> Vanilla.Classic.SurvivalTest.fromString(string)
                POST_SURVIVAL_TEST -> Vanilla.Classic.PostSurvivalTest.fromString(string)
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
        is MinecraftVersion.Vanilla.Classic.PostSurvivalTest -> POST_SURVIVAL_TEST
        is MinecraftVersion.Vanilla.Classic.PreSurvivalTest -> PRE_SURVIVAL_TEST
        is MinecraftVersion.Vanilla.Classic.SurvivalTest -> SURVIVAL_TEST
        MinecraftVersion.Vanilla.Indev -> INDEV
        MinecraftVersion.Vanilla.Infdev -> INFDEV
        is MinecraftVersion.Vanilla.PreClassic -> PRE_CLASSIC
        is MinecraftVersion.Vanilla.Release -> RELEASE
        is MinecraftVersion.Vanilla.Snapshot -> SNAPSHOT
        is MinecraftVersion.Custom -> CUSTOM
    }