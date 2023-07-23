package com.rohengiralt.minecraftservermanager.domain.service

import com.rohengiralt.minecraftservermanager.domain.model.*
import com.rohengiralt.minecraftservermanager.util.ifNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

interface RestAPIService {
    suspend fun getAllServers(): List<MinecraftServer>
    suspend fun createServer(uuid: UUID? = null, name: String, version: MinecraftVersion, runnerUUID: UUID): Boolean
    suspend fun getServer(uuid: UUID): MinecraftServer?
    suspend fun setServer(uuid: UUID, name: String, version: MinecraftVersion, runnerUUID: UUID): Boolean
    suspend fun updateServer(uuid: UUID, name: String? = null): Boolean
    suspend fun deleteServer(uuid: UUID): Boolean

    suspend fun getAllRunners(): List<MinecraftServerRunner>
    suspend fun getRunner(uuid: UUID): MinecraftServerRunner?
    suspend fun getAllCurrentRuns(runnerUUID: UUID): List<MinecraftServerCurrentRun>?
    suspend fun createCurrentRun(serverUUID: UUID, environment: MinecraftServerEnvironment): MinecraftServerCurrentRun?
    suspend fun getCurrentRun(runnerUUID: UUID, runUUID: UUID): MinecraftServerCurrentRun?
    suspend fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun?
    suspend fun stopCurrentRun(runnerUUID: UUID, runUUID: UUID): Boolean
    suspend fun stopCurrentRunByServer(serverUUID: UUID): Boolean
    suspend fun stopAllCurrentRuns(runnerUUID: UUID): Boolean

    suspend fun getAllPastRuns(serverUUID: UUID): List<MinecraftServerPastRun>
    suspend fun getPastRun(serverUUID: UUID, runUUID: UUID): MinecraftServerPastRun?
}

class RestAPIServiceImpl : RestAPIService, KoinComponent {
    override suspend fun getAllServers(): List<MinecraftServer> =
        serverRepository.getAllServers()

    override suspend fun createServer(uuid: UUID?, name: String, version: MinecraftVersion, runnerUUID: UUID): Boolean {
        val server = MinecraftServer(
            uuid = uuid ?: UUID.randomUUID(),
            name = name,
            version = version,
            runnerUUID
        )

        val success = runnerRepository.getRunner(runnerUUID)?.initializeServer(server) ?: false

        if (!success) {
            return false
        }

        return serverRepository.addServer(server)
    }

    override suspend fun getServer(uuid: UUID): MinecraftServer? =
        serverRepository.getServer(uuid)

    override suspend fun setServer(uuid: UUID, name: String, version: MinecraftVersion, runnerUUID: UUID): Boolean =
        serverRepository.saveServer(MinecraftServer(uuid = uuid, name = name, version = version, runnerUUID = runnerUUID))

    override suspend fun updateServer(uuid: UUID, name: String?): Boolean {
        val server = serverRepository.getServer(uuid) ?: return false // TODO: CONCURRENCY CONTROL/TRANSACTION MANAGEMENT
        name?.let { server.name = it }

        return serverRepository.saveServer(server)
    }

    override suspend fun deleteServer(uuid: UUID): Boolean {
        val server = serverRepository.getServer(uuid) ?: return false
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return false
        runner.removeServer(server)
        return serverRepository.removeServer(uuid)
    }

    override suspend fun getAllRunners(): List<MinecraftServerRunner> =
        runnerRepository.getAllRunners()

    override suspend fun getRunner(uuid: UUID): MinecraftServerRunner? =
        runnerRepository.getRunner(uuid)

//    override suspend fun isRunning(serverUUID: UUID): Boolean? =
//        getAllCurrentRuns(serverUUID, runnerUUID = null)?.isNotEmpty()

    override suspend fun getAllCurrentRuns(runnerUUID: UUID): List<MinecraftServerCurrentRun>? {
        val runner =
            runnerRepository.getRunner(runnerUUID).ifNull {
                println("Could not find runner")
                return null
            }

        return runner.getAllCurrentRuns()
    }

    override suspend fun createCurrentRun(serverUUID: UUID, environment: MinecraftServerEnvironment): MinecraftServerCurrentRun? {
        val server = serverRepository.getServer(serverUUID).ifNull {
            println("Couldn't find server for UUID $serverUUID")
            return null
        }
        val runner = runnerRepository.getRunner(server.runnerUUID).ifNull {
            println("Couldn't find runner for UUID $server.runnerUUID")
            return null
        }

        if (runner.isRunning(serverUUID)) { // TODO: possible concurrency issues if another run starts before this one?
            println("Cannot create current run because server $serverUUID is already running")
            return null
        }

        return runner.runServer(server, environment)
    }

    override suspend fun getCurrentRun(runnerUUID: UUID, runUUID: UUID): MinecraftServerCurrentRun? {
        val runner = runnerRepository.getRunner(runnerUUID) ?: return null

        return runner.getCurrentRun(runUUID)
    }

    override suspend fun getCurrentRunByServer(serverUUID: UUID): MinecraftServerCurrentRun? {
        val runner = getRunnerByServer(serverUUID)

        return runner?.getCurrentRunByServer(serverUUID)
    }

    override suspend fun stopCurrentRun(runnerUUID: UUID, runUUID: UUID): Boolean {
        println("Stopping current run $runUUID")
        val runner = runnerRepository.getRunner(runnerUUID) ?: return false

        return runner.stopRun(runUUID)
    }

    override suspend fun stopCurrentRunByServer(serverUUID: UUID): Boolean {
        println("Stopping current run of server $serverUUID")
        val server = serverRepository.getServer(serverUUID) ?: return false
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return false

        return runner.stopRunByServer(serverUUID)
    }

    override suspend fun stopAllCurrentRuns(runnerUUID: UUID): Boolean {
        println("Stopping all current runs for runner $runnerUUID")
        val runner = runnerRepository.getRunner(runnerUUID)?: return false

        return runner.stopAllRuns()
    }

    override suspend fun getAllPastRuns(serverUUID: UUID): List<MinecraftServerPastRun> =
        pastRunRepository.getAllPastRuns(serverUUID)

    override suspend fun getPastRun(serverUUID: UUID, runUUID: UUID): MinecraftServerPastRun? =
        pastRunRepository.getPastRun(serverUUID)

    private fun getRunnerByServer(serverUUID: UUID): MinecraftServerRunner? {
        val server = serverRepository.getServer(serverUUID).ifNull {
            println("Couldn't find server for UUID $serverUUID")
            return null
        }

        val runner = runnerRepository.getRunner(server.runnerUUID).ifNull {
            println("Couldn't find runner for UUID $server.runnerUUID")
            return null
        }

        return runner
    }

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()
}