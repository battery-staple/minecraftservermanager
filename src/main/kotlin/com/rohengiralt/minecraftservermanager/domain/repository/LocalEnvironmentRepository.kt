package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.LocalMinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.insertSuccess
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.sql.suspendIOExnTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

class LocalEnvironmentRepository : EnvironmentRepository, KoinComponent {
    // TODO: Store in database but maintain in-memory cache due to state (currentProcess)

    init {
        transaction { SchemaUtils.create(LocalEnvironmentTable) }
    }

    override suspend fun getEnvironment(environmentUUID: UUID): LocalMinecraftServerEnvironment? = suspendIOExnTransaction {
        LocalEnvironmentTable
            .select { LocalEnvironmentTable.uuid eq environmentUUID }
            .firstOrNull()
            ?.toEnvironment()
            ?.also { assert(it.uuid == environmentUUID) }
    }

    override suspend fun getEnvironmentByServer(serverUUID: UUID): LocalMinecraftServerEnvironment? = suspendIOExnTransaction {
        LocalEnvironmentTable
            .select { LocalEnvironmentTable.serverUUID eq serverUUID }
            .firstOrNull()
            ?.toEnvironment()
            ?.also { assert(it.serverUUID == serverUUID) }
    }

    override suspend fun getAllEnvironments(): List<LocalMinecraftServerEnvironment> = suspendIOExnTransaction {
        LocalEnvironmentTable
            .selectAll()
            .mapNotNull { it.toEnvironment() }
    }

    override suspend fun addEnvironment(environment: MinecraftServerEnvironment): Boolean = suspendIOExnTransaction {
        if (environment !is LocalMinecraftServerEnvironment) {
            logger.warn("Cannot persist environment of type {}", environment::class.simpleName)
            return@suspendIOExnTransaction false
        }

        LocalEnvironmentTable
            .insertSuccess {
                it[uuid] = environment.uuid
                it[serverUUID] = environment.serverUUID
                it[jarVersion] = environment.jar.version
            }

        return@suspendIOExnTransaction true
    }

    override suspend fun removeEnvironment(environment: MinecraftServerEnvironment): Boolean = suspendIOExnTransaction {
        if (environment !is LocalMinecraftServerEnvironment) {
            logger.warn("Cannot remove environment of type {}", environment::class.simpleName)
            return@suspendIOExnTransaction false
        }

        val rowsDeleted = LocalEnvironmentTable
            .deleteWhere { LocalEnvironmentTable.uuid eq environment.uuid }

        return@suspendIOExnTransaction rowsDeleted > 0
    }

    private suspend fun ResultRow.toEnvironment(): LocalMinecraftServerEnvironment? {
        val uuid = this[LocalEnvironmentTable.uuid]
        val serverUUID = this[LocalEnvironmentTable.serverUUID]

        val server = minecraftServers.getServer(serverUUID)
        if (server == null) {
            logger.error("Inconsistent database state: Environment {} references server {}, which does not exist", this[LocalEnvironmentTable.uuid], serverUUID)
            return null
        }

        val contentDirectory = contentDirectories.getExistingContentDirectory(serverUUID)
        if (contentDirectory == null) {
            logger.error("Inconsistent database state: Environment {} references content directory for server {}, which does not exist", this[LocalEnvironmentTable.uuid], serverUUID)
            return null
        }

        val version = this[LocalEnvironmentTable.jarVersion]

        val jar = jars.accessJar(version, uuid)
        if (jar == null) {
            logger.error("Failed to retrieve environment {}; unable to access jar with version {}", uuid, version.versionString)
            return null
        }

        return LocalMinecraftServerEnvironment(
            uuid = uuid,
            serverUUID = serverUUID,
            serverName = server.name,
            contentDirectory = contentDirectory,
            jar = jar
        )
    }

    private val minecraftServers: MinecraftServerRepository by inject()
    private val contentDirectories: LocalMinecraftServerContentDirectoryRepository by inject()
    private val jars: MinecraftServerJarResourceManager by inject()

    private val logger = LoggerFactory.getLogger(LocalEnvironmentRepository::class.java)
}

private object LocalEnvironmentTable : Table() {
    val uuid = uuid("uuid")
    val serverUUID = uuid("server_uuid")
//    val contentDirectory = text("path") TODO: store directly here rather than pulling from contentDirectoryRepository
    val jarVersion = jsonb("version", MinecraftVersion.serializer())

    override val primaryKey = PrimaryKey(uuid)
}