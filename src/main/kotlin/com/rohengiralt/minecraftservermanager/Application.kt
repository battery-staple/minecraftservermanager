package com.rohengiralt.minecraftservermanager

import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.MinecraftJarAPI
import com.rohengiralt.minecraftservermanager.domain.infrastructure.minecraftJarApi.RedundantFallbackAPI
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryFactory
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.APIMinecraftServerJarFactory
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.FilesystemMinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarFactory
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.repository.*
import com.rohengiralt.minecraftservermanager.domain.service.MonitorAPIService
import com.rohengiralt.minecraftservermanager.domain.service.MonitorAPIServiceImpl
import com.rohengiralt.minecraftservermanager.domain.service.WebsocketAPIService
import com.rohengiralt.minecraftservermanager.domain.service.WebsocketAPIServiceImpl
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIServiceImpl
import com.rohengiralt.minecraftservermanager.plugins.configureMonitoring
import com.rohengiralt.minecraftservermanager.plugins.configureRouting
import com.rohengiralt.minecraftservermanager.plugins.configureSecurity
import com.rohengiralt.minecraftservermanager.user.auth.google.UserIDAuthorizer
import com.rohengiralt.minecraftservermanager.user.auth.google.WhitelistFileUserIDAuthorizer
import com.rohengiralt.minecraftservermanager.user.preferences.DatabaseUserPreferencesRepository
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferencesRepository
import com.rohengiralt.shared.serverProcess.MinecraftServerDispatcher
import com.rohengiralt.shared.util.assertsEnabled
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger
import org.slf4j.LoggerFactory

fun main() {
    logger.info("Starting")

    logger.info("Asserts are " + if (assertsEnabled) "ENABLED" else "DISABLED")
    logger.info("Debug mode is " + if (debugMode) "ENABLED" else "DISABLED")

    runBlocking {
        logger.info("Initializing database")
        initDatabase(10)
    }

    logger.info("Initializing server")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    logger.info("Configuring koin")
    install(Koin) {
        SLF4JLogger()
        modules(
            module(createdAtStart = true) {
                single<HttpClient> {
                    HttpClient(OkHttp /*Java //JDK 11 only (or CIO when it supports HTTP 2)*/) {
                        expectSuccess = false

                        install(ContentNegotiation) {
                            json(Json { ignoreUnknownKeys = true })
                        }
                    }
                }
                single<UserIDAuthorizer> { WhitelistFileUserIDAuthorizer() }
                single<Json> { Json { ignoreUnknownKeys = false } }
                single<MinecraftServerRepository> { DatabaseMinecraftServerRepository() }
                single<MinecraftJarAPI> { RedundantFallbackAPI() }
                single<MinecraftServerPastRunRepository> { DatabaseMinecraftServerPastRunRepository() }
                single<MinecraftServerDispatcher> { MinecraftServerDispatcher() }
                single<MinecraftServerJarFactory> { APIMinecraftServerJarFactory() }
                single<MinecraftServerJarResourceManager> {
                    FilesystemMinecraftServerJarResourceManager("/minecraftservermanager/local/jars")
                }
                single<LocalMinecraftServerContentDirectoryFactory> {
                    LocalMinecraftServerContentDirectoryFactory(
                        "/minecraftservermanager/local/servers"
                    )
                }
                single<MinecraftServerRunnerRepository>(createdAtStart = true) { // Needs to be created at start to archive old currentRuns
                    HardcodedMinecraftServerRunnerRepository()
                }
                single<LocalEnvironmentRepository> { LocalEnvironmentRepository() }
                single<CurrentRunRepository> { InMemoryCurrentRunRepository() }
                single<MinecraftServerCurrentRunRecordRepository> { DatabaseMinecraftServerCurrentRunRecordRepository() }
                single<UserPreferencesRepository> { DatabaseUserPreferencesRepository() }

                single<RestAPIService> { RestAPIServiceImpl() }
                single<WebsocketAPIService> { WebsocketAPIServiceImpl() }
                single<MonitorAPIService> { MonitorAPIServiceImpl() }
            },
        )
    }

    logger.info("Configuring security")
    configureSecurity()
    logger.info("Configuring routing")
    configureRouting()
    logger.info("Configuring monitoring")
    configureMonitoring()
}

private val logger = LoggerFactory.getLogger("Main")