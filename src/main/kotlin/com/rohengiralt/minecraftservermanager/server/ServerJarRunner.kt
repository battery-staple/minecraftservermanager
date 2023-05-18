package com.rohengiralt.minecraftservermanager.server
//
//import com.rohengiralt.minecraftservermanager.model.Port
//import java.nio.file.Path
//import kotlin.io.path.absolutePathString
//import kotlin.io.path.div
//import kotlin.io.path.writeText
//
//internal interface ServerJarRunner {
//    fun start(jar: Path, port: Port, contentDirectory: Path, minSpaceMegabytes: UInt = 1024U, maxSpaceMegabytes: UInt = 2048U): Process
//}
//
//class ServerJarRunnerImpl(private val javaExecutable: String = "java") : ServerJarRunner {
////    private val directory = "/tmp/minecraftservermanager/servers" //TODO: Customizable
//
////    init {
////        Path(directory).createDirectories()
////    }
//
//    override fun start(jar: Path, port: Port, contentDirectory: Path, minSpaceMegabytes: UInt, maxSpaceMegabytes: UInt): Process {
////        val runDirectory = (sequenceOf(Path(directory)/jar.nameWithoutExtension) +
////            generateSequence(1) { it + 1 }.map { Path(directory)/(jar.nameWithoutExtension + it) })
////            .take(100) // TODO: Configurable maximum
////            .firstOrNull { it.notExists() || it.isDirectory() }
////            ?.also { if (it.notExists()) it.createDirectory() }
////            ?: error("All acceptable directory names taken")
//
//        (contentDirectory/"eula.txt") // Does this need to be run every time?
//            .writeText("eula=true")
//
//        println("Agreed to EULA")
//        val process = ProcessBuilder(javaExecutable, "-Xms${minSpaceMegabytes}M", "-Xmx${maxSpaceMegabytes}M", "-jar", jar.escapedAbsolutePathString()) //TODO: Customizable heap space
//            .directory(contentDirectory.toFile())
//            .start()
//
//        println("Started process $process")
//        return process
//    }
//
//            private fun Path.escapedAbsolutePathString() =
//                absolutePathString().replace(" ", "\\ ")
//}