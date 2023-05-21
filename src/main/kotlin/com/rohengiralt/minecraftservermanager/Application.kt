package com.rohengiralt.minecraftservermanager

import com.rohengiralt.minecraftservermanager.domain.infrastructure.LocalMinecraftServerDispatcher
import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.MinecraftJarAPI
import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.RedundantFallbackAPI
import com.rohengiralt.minecraftservermanager.domain.model.*
import com.rohengiralt.minecraftservermanager.domain.model.local.contentdirectory.LocalMinecraftServerContentDirectoryFactory
import com.rohengiralt.minecraftservermanager.domain.model.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.local.currentruns.CurrentRunRepository
import com.rohengiralt.minecraftservermanager.domain.model.local.currentruns.InMemoryCurrentRunRepository
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.APIMinecraftServerJarFactory
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.FilesystemCacheMinecraftServerJarRepository
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.MinecraftServerJarFactory
import com.rohengiralt.minecraftservermanager.domain.model.local.serverjar.MinecraftServerJarRepository
import com.rohengiralt.minecraftservermanager.domain.service.RestAPIService
import com.rohengiralt.minecraftservermanager.domain.service.RestAPIServiceImpl
import com.rohengiralt.minecraftservermanager.domain.service.WebsocketAPIService
import com.rohengiralt.minecraftservermanager.domain.service.WebsocketAPIServiceImpl
import com.rohengiralt.minecraftservermanager.plugins.configureMonitoring
import com.rohengiralt.minecraftservermanager.plugins.configureRouting
import com.rohengiralt.minecraftservermanager.plugins.configureSecurity
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger

fun main() {
    println("Starting")
    runBlocking {
        println("Initializing database")
        initDatabase()
    }

    println("Initializing server")

    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    install(Koin) {
        SLF4JLogger()
        @Suppress("RemoveExplicitTypeArguments")
        modules(
            org.koin.dsl.module {
                single<HttpClient> {
                    HttpClient(OkHttp /*Java //JDK 11 only (or CIO when it supports HTTP 2)*/) {
                        expectSuccess = false

                        install(ContentNegotiation) {
                            json(get())
                        }
                    }
                }
                single<Json> { Json { ignoreUnknownKeys = true } }
                single<MinecraftServerRepository> { DatabaseMinecraftServerRepository() }
                single<MinecraftJarAPI> { RedundantFallbackAPI() }
                single<MinecraftServerPastRunRepository> { DatabaseMinecraftServerPastRunRepository() }
                single<LocalMinecraftServerDispatcher> { LocalMinecraftServerDispatcher() }
                single<MinecraftServerJarFactory> { APIMinecraftServerJarFactory() }
                single<MinecraftServerJarRepository> {
                    FilesystemCacheMinecraftServerJarRepository("/minecraftservermanager/local/jars")
                }
                single<LocalMinecraftServerContentDirectoryRepository> {
                    LocalMinecraftServerContentDirectoryRepository()
                }
                single<LocalMinecraftServerContentDirectoryFactory> {
                    LocalMinecraftServerContentDirectoryFactory(
                        "/minecraftservermanager/local/servers"
                    )
                }
                single<MinecraftServerRunnerRepository> { HardcodedMinecraftServerRunnerRepository() }
                single<CurrentRunRepository> { InMemoryCurrentRunRepository() }

                single<RestAPIService> { RestAPIServiceImpl() }
                single<WebsocketAPIService> { WebsocketAPIServiceImpl() }
            },

//                MinecraftServerStorageRepository.koinModule,
//                ServerRunningManager.koinModule,
//                ServerJarRepository.koinModule,
//                ServerRuntimeDirectoryRepository.koinModule,
//                ServerJarFactory.koinModule
        )
    }

    configureRouting()
    configureSecurity()
    configureMonitoring()
}