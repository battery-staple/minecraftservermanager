package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.LocalMinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.contentdirectory.LocalMinecraftServerContentDirectoryRepository
import com.rohengiralt.minecraftservermanager.domain.model.runner.local.serverjar.MinecraftServerJarResourceManager
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.insertSuccess
import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.logger
import com.rohengiralt.minecraftservermanager.util.sql.suspendIOExnTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A repository designed to store [LocalMinecraftServerEnvironment]s.
 * Environments are stored primarily in memory, but persisted in the database
 * for retrieval after server restarts.
 */
class LocalEnvironmentRepository : EnvironmentRepository<LocalMinecraftServerEnvironment>, KoinComponent {
    /**
     * An in-memory cache for environments.
     * This serves to speed up accesses and reduce database load,
     * but more importantly prevents creating duplicate environments.
     * Since environments are stateful, creating duplicates could lead to confusing bugs.
     *
     * This cache is never manually cleared except when an environment is deleted.
     * It should be the primary source of truth for environments, with the database only as a backup.
     */
    private val cache: EnvironmentRepository<LocalMinecraftServerEnvironment> = InMemoryEnvironmentRepository()
    private val cacheLock: Mutex = Mutex()

    /**
     * Contains all environments from the database.
     * This should only be accessed as a last resort, particularly after server restarts.
     */
    private val db: DatabaseLocalEnvironmentRepository = DatabaseLocalEnvironmentRepository()

    override suspend fun getEnvironment(environmentUUID: UUID): LocalMinecraftServerEnvironment? = cacheLock.withLock {
        cache.getEnvironment(environmentUUID)
            ?: getFromDatabaseAndCache(environmentUUID)
    }

    override suspend fun getEnvironmentByServer(serverUUID: UUID): LocalMinecraftServerEnvironment? = cacheLock.withLock {
        cache.getEnvironmentByServer(serverUUID)
            ?: getByServerFromDatabaseAndCache(serverUUID)
    }

    /**
     * Retrieves an environment from the database by its `uuid`, caching it.
     */
    private suspend fun getFromDatabaseAndCache(environmentUUID: UUID): LocalMinecraftServerEnvironment? =
        db.getEnvironment(environmentUUID)?.also { cache.addEnvironment(it) }

    /**
     * Retrieves an environment from the database by its `serverUUID`, caching it
     */
    private suspend fun getByServerFromDatabaseAndCache(serverUUID: UUID): LocalMinecraftServerEnvironment? =
        db.getEnvironmentByServer(serverUUID)?.also { cache.addEnvironment(it) }

    override suspend fun getAllEnvironments(): List<LocalMinecraftServerEnvironment> =
        // Database is only source of truth for all envs, but we want to pull the version from cache if it exists
        db.getAllEnvironmentUUIDs()
            .mapNotNull { uuid -> getEnvironment(uuid) }

    override suspend fun addEnvironment(environment: LocalMinecraftServerEnvironment): Boolean {
        cacheLock.withLock {
            val cacheSuccess = cache.addEnvironment(environment)
            if (!cacheSuccess) {
                logger.error("Failed to cache environment {}", environment.uuid)
                // Don't short circuit return; try to at least store it in the database
            }

            val dbSuccess = db.addEnvironment(environment)
            if (!dbSuccess) {
                logger.error("Failed to persist environment {}", environment.uuid)
                return false
            }

            @Suppress("KotlinConstantConditions")
            return cacheSuccess && dbSuccess
        }
    }

    override suspend fun removeEnvironment(environment: LocalMinecraftServerEnvironment): Boolean {
        cacheLock.withLock {
            val cacheSuccess = cache.removeEnvironment(environment)
            if (!cacheSuccess) {
                logger.error("Failed to remove cached environment {}", environment.uuid)
                return false
            }

            val dbSuccess = db.removeEnvironment(environment)
            if (!dbSuccess) {
                logger.error("Failed to remove persisted environment {}", environment.uuid)
                return false
            }

            return true
        }
    }
}

/**
 * An [EnvironmentRepository] that exclusively stores environments in the database.
 * This can lead to unexpected behavior as [MinecraftServerEnvironment]s can be stateful.
 * Thus, this class is only safe to use as a backing for an in-memory cache.
 */
private class DatabaseLocalEnvironmentRepository : EnvironmentRepository<LocalMinecraftServerEnvironment>, KoinComponent {
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

    suspend fun getAllEnvironmentUUIDs(): List<UUID> = suspendIOExnTransaction {
        LocalEnvironmentTable
            .selectAll()
            .map { it[LocalEnvironmentTable.uuid] }
    }

    override suspend fun addEnvironment(environment: LocalMinecraftServerEnvironment): Boolean = suspendIOExnTransaction {
        LocalEnvironmentTable
            .insertSuccess {
                it[uuid] = environment.uuid
                it[serverUUID] = environment.serverUUID
                it[jarVersion] = environment.jar.version
            }

        return@suspendIOExnTransaction true
    }

    override suspend fun removeEnvironment(environment: LocalMinecraftServerEnvironment): Boolean = suspendIOExnTransaction {
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