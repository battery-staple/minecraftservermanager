val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlinx.atomicfu") version "0.25.0"
}

group = "com.rohengiralt"
version = "0.0.1"

application {
    mainClass.set("com.rohengiralt.minecraftservermanager.ApplicationKt")
}

project.setProperty("mainClassName", "com.rohengiralt.minecraftservermanager.ApplicationKt")

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":shared"))

    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-sessions:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-host-common:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-html-builder:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version") {
        exclude(group = "pull-parser", module = "pull-parser") // https://stackoverflow.com/questions/71910861/failed-to-auto-configure-default-logger-context-joranexception-parser-configu
    }

    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-java:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-auth:$ktor_version")

    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.2.1")
    implementation("org.jetbrains.exposed:exposed-core:0.34.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.34.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.34.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.34.1")

    implementation("com.google.api-client:google-api-client:2.6.0")
    implementation("io.kubernetes:client-java:20.0.0")

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("io.insert-koin:koin-ktor:3.3.1")
    implementation("io.insert-koin:koin-logger-slf4j:3.3.1")
    implementation("com.uchuhimo:konf:1.1.2") {
        exclude(group = "pull-parser", module = "pull-parser") // https://stackoverflow.com/questions/71910861/failed-to-auto-configure-default-logger-context-joranexception-parser-configu
    }
    implementation("com.google.guava:guava:33.2.1-jre")

    implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-encoding:$ktor_version")
    implementation("io.ktor:ktor-server-host-common-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp-jvm:$ktor_version")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
}
