//package com.rohengiralt.minecraftservermanager.server
//
//import com.rohengiralt.minecraftservermanager.observation.Observer
//import com.rohengiralt.minecraftservermanager.util.extensions.exposed.jsonb
//import com.rohengiralt.minecraftservermanager.util.extensions.exposed.upsert
//import com.rohengiralt.minecraftservermanager.util.extensions.mutableMap.getReference
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.asFlow
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.flow.onEach
//import kotlinx.coroutines.flow.toList
//import kotlinx.coroutines.sync.Mutex
//import kotlinx.coroutines.sync.withLock
//import org.jetbrains.exposed.exceptions.ExposedSQLException
//import org.jetbrains.exposed.sql.*
//import org.jetbrains.exposed.sql.transactions.transaction
//import org.koin.core.component.KoinComponent
//import org.koin.core.component.inject
//import org.koin.dsl.module
//import java.lang.ref.WeakReference
//import java.util.*
//
//interface MinecraftServerRepository {
//    suspend fun saveServer(server: MinecraftServer): Boolean
//    suspend fun removeServer(uuid: UUID): Boolean
//
//    suspend fun getServer(uuid: UUID): MinecraftServer?
//    suspend fun getAllServers(): List<MinecraftServer>
//
//    companion object {
//        val koinModule = module {
//            single<MinecraftServerRepository> { UniqueNameMinecraftServerRepository(UniqueNameMinecraftServerStorageRepository()) }
//        }
//    }
//}
//
//class UniqueNameMinecraftServerRepository(private val storageRepository: MinecraftServerStorageRepository) : MinecraftServerRepository, KoinComponent {
//    private val serverUpdaters: MutableMap<UUID, WeakReference<MinecraftServerUpdater>> = mutableMapOf()
//    private val serverUpdatersMutex: Mutex = Mutex()
//
//    private val serverFactory: MinecraftServerFactory by inject()
//
//    override suspend fun saveServer(server: MinecraftServer): Boolean {
//        storageRepository.setOrAddServer(MinecraftServerStorage(server))
//        // TODO: What if the server async updates in this time?
//        serverUpdatersMutex.withLock {
//            if (serverUpdaters[server.uuid] == null)
//                saveNewMinecraftServerUpdater(server = server)
//        }
//
//        return true
//    }
//
//    override suspend fun removeServer(uuid: UUID): Boolean {
//        serverUpdatersMutex.withLock {
//            val serverUpdater = serverUpdaters.getReference(uuid) ?: return false
//            serverUpdater.mutex.withLock { // Using two resources at once; TODO: ENSURE NO DEADLOCK
//                serverUpdater.valid = false
//                serverUpdaters.remove(uuid)
//                // No more strong references to serverUpdater
//                // (its usage as Observer is a WeakReference),
//                // so will be garbage collected eventually.
//            }
//        }
//
//        return storageRepository.deleteServer(uuid = uuid)
//    }
//
//    override suspend fun getServer(uuid: UUID): MinecraftServer? =
//        serverUpdaters.getReference(uuid)?.server ?:
//            storageRepository.getServer(uuid)?.toMinecraftServer()?.also { storedServer ->
//                serverUpdatersMutex.withLock {
//                    saveNewMinecraftServerUpdater(storedServer)
//                }
//            }
//
//    override suspend fun getAllServers(): List<MinecraftServer> =
//        storageRepository
//            .getAllServers()
//            .asFlow()
//            .map(MinecraftServerStorage::toMinecraftServer)
//            .onEach { storedServer ->
//                val cachedUpdaterExists = serverUpdatersMutex.withLock {
//                    serverUpdaters[storedServer.uuid] != null
//                }
//
//                if (cachedUpdaterExists) {
//                    saveNewMinecraftServerUpdater(storedServer)
//                }
//            }
//            .toList()
//
//    // MUST BE CALLED WITH serverUpdatersMutex
//    private fun saveNewMinecraftServerUpdater(server: MinecraftServer) {
//        MinecraftServerUpdater(server = server).let {
//            serverUpdaters[server.uuid] = WeakReference(it)
//        }
//    }
//
//    private inner class MinecraftServerUpdater(val server: MinecraftServer) : Observer {
//        val mutex = Mutex()
//        var valid = true
//            set(value) {
//                if (!value) {
//                    scope.cancel()
//                }
//                field = value
//            }
//
//        private val scope: CoroutineScope = CoroutineScope(SupervisorJob())
//        override fun update() {
//            scope.launch {
//                storageRepository.setOrAddServer(MinecraftServerStorage(server))
//            }
//        }
//    }
//}
//
//interface MinecraftServerStorageRepository { // TODO: Like half of these methods can be pruned
//    suspend fun getServer(uuid: UUID): MinecraftServerStorage?
//    suspend fun getAllServers(): List<MinecraftServerStorage>
//    suspend fun addServer(server: MinecraftServerStorage): Boolean
//    suspend fun setOrAddServer(server: MinecraftServerStorage)
//    suspend fun deleteServer(where: (MinecraftServerStorage) -> Boolean): Boolean
//    suspend fun deleteServer(uuid: UUID): Boolean
//
//    companion object {
//        val koinModule = module {
//            single<MinecraftServerStorageRepository> { UniqueNameMinecraftServerStorageRepository() }
//        }
//    }
//}
//
//
//private class UniqueNameMinecraftServerStorageRepository : MinecraftServerStorageRepository, KoinComponent {
//    init {
//        transaction {
//            SchemaUtils.create(MinecraftServerTable)
//        }
//    }
//
//    @OptIn(ExperimentalCoroutinesApi::class)
//    private val updatingServerTableCoroutineContext = newSingleThreadContext("serverTableUpdater")
//
//    override suspend fun getServer(uuid: UUID): MinecraftServerStorage? =
//        withContext(updatingServerTableCoroutineContext) {
//            transaction {
//                MinecraftServerTable.select {
//                    MinecraftServerTable.uuid eq uuid
//                }.singleOrNull()?.let { it[MinecraftServerTable.server] }
//            }
//        }
//
//    override suspend fun getAllServers(): List<MinecraftServerStorage> =
//        withContext(updatingServerTableCoroutineContext) {
//            transaction {
//                MinecraftServerTable.selectAll().map { it[MinecraftServerTable.server] }
//            }
//        }
//
//    override suspend fun addServer(server: MinecraftServerStorage): Boolean {
//        require(server.name.length <= 100)
//        return withContext(updatingServerTableCoroutineContext) {
//            transaction {
//                try {
//                    MinecraftServerTable.insert {
//                        it[this.uuid] = server.uuid
//                        it[this.name] = server.name
//                        it[this.server] = server
//                    }
//                    true
//                } catch (e: ExposedSQLException) { //TODO: More specific
//                    false
//                }
//            }
//        }
//    }
//
//    override suspend fun setOrAddServer(server: MinecraftServerStorage) {
//        require(server.name.length <= 100)
//        withContext(updatingServerTableCoroutineContext) {
//            transaction {
//                MinecraftServerTable.upsert(MinecraftServerTable.uuid, MinecraftServerTable.name) {
//                    it[this.uuid] = server.uuid
//                    it[this.name] = server.name
//                    it[this.server] = server
//                }
//            }
//        }
//    }
//
//    override suspend fun deleteServer(where: (MinecraftServerStorage) -> Boolean): Boolean {
//        val rowsDeleted: Int
//        withContext(updatingServerTableCoroutineContext) {
//            val idsToDelete = transaction {
//                MinecraftServerTable.selectAll().map {
//                    it[MinecraftServerTable.server]
//                }.filter(where).map { it.uuid }
//            }
//            rowsDeleted = transaction {
//                MinecraftServerTable.deleteWhere { MinecraftServerTable.uuid inList idsToDelete }
//            }
//        }
//
//        return rowsDeleted > 0
//    }
//
//    override suspend fun deleteServer(uuid: UUID): Boolean {
//        val rowsDeleted: Int = withContext(updatingServerTableCoroutineContext) {
//            transaction {
//                MinecraftServerTable.deleteWhere { MinecraftServerTable.uuid eq uuid }
//            }
//        }
//
//        return rowsDeleted > 0
//    }
//}
//
//private object MinecraftServerTable : Table() {
//    val uuid: Column<UUID> = uuid("uuid")
//    val name: Column<String> = varchar("name", 100).uniqueIndex()
//    val server: Column<MinecraftServerStorage> = jsonb("serverData", MinecraftServerStorage.serializer())
//
//    override val primaryKey: PrimaryKey = PrimaryKey(uuid)
//}
