package com.rohengiralt.minecraftservermanager.domain.service.rest

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerPastRunRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRunnerRepository
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Companion.Success
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Failure
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Success
import com.rohengiralt.minecraftservermanager.frontend.model.UserPreferencesAPIModel
import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferences
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferencesRepository
import com.rohengiralt.minecraftservermanager.util.ifNull
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.errors.*
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

class RestAPIServiceImpl : RestAPIService, KoinComponent {
    override suspend fun getAllServers(): APIResult<List<MinecraftServer>> =
        Success(serverRepository.getAllServers())

    override suspend fun createServer(uuid: UUID?, name: String, version: MinecraftVersion, runnerUUID: UUID): Boolean {
        val server = MinecraftServer(
            uuid = uuid ?: UUID.randomUUID(),
            name = name,
            version = version,
            runnerUUID = runnerUUID,
            creationTime = Clock.System.now()
        )

        val success = runnerRepository.getRunner(runnerUUID)?.initializeServer(server) ?: false

        if (!success) {
            return false
        }

        return serverRepository.addServer(server)
    }

    override suspend fun getServer(uuid: UUID): APIResult<MinecraftServer> =
        try {
            serverRepository.getServer(uuid)
                ?.let { Success(it) }
                ?: Failure.NotFound(uuid)
        } catch (e: IOException) {
            Failure.Unknown(e)
        }

    override suspend fun setServer(uuid: UUID, name: String, version: MinecraftVersion, runnerUUID: UUID): Boolean =
        serverRepository.saveServer(
            MinecraftServer(
                uuid = uuid,
                name = name,
                version = version,
                runnerUUID = runnerUUID,
                creationTime = Clock.System.now()
            )
        )

    override suspend fun updateServer(uuid: UUID, name: String?): APIResult<Unit> {
        // TODO: CONCURRENCY CONTROL/TRANSACTION MANAGEMENT

        val server = serverRepository.getServer(uuid) ?: return Failure.NotFound(uuid)

        name?.let { server.name = it }

        return if (serverRepository.saveServer(server)) Success() else Failure.Unknown()
    }

    override suspend fun deleteServer(uuid: UUID): Boolean {
        logger.trace("Getting server with uuid {}", uuid)
        val server = serverRepository.getServer(uuid) ?: return false
        logger.trace("Getting runner with uuid {}", uuid)
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return false
        logger.trace("Removing server with uuid {} from runner with uuid {}", uuid, runner.uuid)
        val removeFromRunnerSuccess = runner.removeServer(server)
        logger.trace("Removing server with uuid {} from runner with uuid {} {}", uuid, runner.uuid, if (removeFromRunnerSuccess) "SUCCEEDED" else "FAILED")
        if (!removeFromRunnerSuccess) {
            return false // Early exit; don't remove it from the database unless it's been fully cleaned up. TODO: some better transactionality?
        }
        logger.trace("Removing server with uuid {} from server repository}", uuid)
        val removeFromRepositorySuccess = serverRepository.removeServer(uuid)
        logger.trace("Removing server with uuid {} from server repository {}}", uuid, if (removeFromRepositorySuccess) "SUCCEEDED" else "FAILED")
        return removeFromRepositorySuccess
    }

    override suspend fun getAllRunners(): List<MinecraftServerRunner> =
        runnerRepository.getAllRunners()

    override suspend fun getRunner(uuid: UUID): MinecraftServerRunner? =
        runnerRepository.getRunner(uuid)

    override suspend fun getAllCurrentRuns(runnerUUID: UUID): List<MinecraftServerCurrentRun>? {
        val runner =
            runnerRepository.getRunner(runnerUUID).ifNull {
                logger.trace("Could not find runner")
                return null
            }

        return runner.getAllCurrentRuns()
    }

    override suspend fun createCurrentRun(serverUUID: UUID, environment: MinecraftServerEnvironment): MinecraftServerCurrentRun? {
        val server = serverRepository.getServer(serverUUID).ifNull {
            logger.trace("Couldn't find server for UUID {}", serverUUID)
            return null
        }

        val runner = runnerRepository.getRunner(server.runnerUUID).ifNull {
            logger.trace("Couldn't find runner for UUID {}.runnerUUID", server)
            return null
        }

        if (runner.isRunning(serverUUID)) { // TODO: possible concurrency issues if another run starts before this one?
            logger.trace("Cannot create current run because server {} is already running", serverUUID)
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
        logger.info("Stopping current run $runUUID")
        val runner = runnerRepository.getRunner(runnerUUID) ?: return false

        return runner.stopRun(runUUID)
    }

    override suspend fun stopCurrentRunByServer(serverUUID: UUID): Boolean {
        logger.info("Stopping current run of server $serverUUID")
        val server = serverRepository.getServer(serverUUID) ?: return false
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return false

        return runner.stopRunByServer(serverUUID)
    }

    override suspend fun stopAllCurrentRuns(runnerUUID: UUID): Boolean {
        logger.info("Stopping all current runs for runner $runnerUUID")
        val runner = runnerRepository.getRunner(runnerUUID)?: return false

        return runner.stopAllRuns()
    }

    override suspend fun getAllPastRuns(serverUUID: UUID): List<MinecraftServerPastRun> =
        pastRunRepository.getAllPastRuns(serverUUID)

    override suspend fun getPastRun(serverUUID: UUID, runUUID: UUID): MinecraftServerPastRun? =
        pastRunRepository.getPastRun(runUUID)

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun getCurrentUserLoginInfo(): UserLoginInfo? = call.principal<UserLoginInfo>()

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun deleteCurrentUser(): Boolean {
        return true // Users aren't actually stored at this point
    }

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun getCurrentUserPreferences(): UserPreferences? {
        val loginInfo = getCurrentUserLoginInfo() ?: return null

        return getUserPreferences(loginInfo.userId)
    }

    private suspend fun getUserPreferences(userId: UserID): UserPreferences? =
        userPreferencesRepository.getUserPreferencesOrSetDefaultOrNull(userId = userId)

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun updateCurrentUserPreferences(sortStrategy: UserPreferences.SortStrategy?): Boolean {
        val loginInfo = getCurrentUserLoginInfo() ?: return false

        return updateUserPreferences(loginInfo.userId, sortStrategy)
    }

    private suspend fun updateUserPreferences(userId: UserID, sortStrategy: UserPreferences.SortStrategy?): Boolean {
        val oldPreferences = userPreferencesRepository.getUserPreferencesOrSetDefaultOrNull(userId = userId) ?: return false

        val preferencesModel = UserPreferencesAPIModel(oldPreferences)
        sortStrategy?.let { preferencesModel.serverSortStrategy = it }
        val newPreferences = preferencesModel.toUserPreferences() ?: return false

        return userPreferencesRepository.setUserPreferences(userId = userId, preferences = newPreferences)
    }

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun deleteCurrentUserPreferences(): Boolean {
        val userLoginInfo = getCurrentUserLoginInfo() ?: return false
        return userPreferencesRepository.deleteUserPreferences(userLoginInfo.userId)
    }

    private fun getRunnerByServer(serverUUID: UUID): MinecraftServerRunner? {
        val server = serverRepository.getServer(serverUUID).ifNull {
            logger.trace("Couldn't find server for UUID {}", serverUUID)
            return null
        }

        val runner = runnerRepository.getRunner(server.runnerUUID).ifNull {
            logger.trace("Couldn't find runner for UUID {}.runnerUUID", server)
            return null
        }

        return runner
    }

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()
    private val userPreferencesRepository: UserPreferencesRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
}