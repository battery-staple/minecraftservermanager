package com.rohengiralt.minecraftservermanager.domain.service.rest

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerPastRunRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRunnerRepository
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Companion.Success
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Failure
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Success
import com.rohengiralt.minecraftservermanager.frontend.model.UserPreferencesAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.orElse
import com.rohengiralt.minecraftservermanager.user.UserID
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferences
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferencesRepository
import com.rohengiralt.minecraftservermanager.util.ifNull
import com.rohengiralt.minecraftservermanager.util.ifTrue.ifFalse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.pipeline.*
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

class RestAPIServiceImpl : RestAPIService, KoinComponent {
    override suspend fun getAllServers(): APIResult<List<MinecraftServer>> =
        Success(serverRepository.getAllServers())

    override suspend fun createServer(uuid: UUID?, name: String, version: MinecraftVersion, runnerUUID: UUID): APIResult<MinecraftServer> {
        val server = MinecraftServer(
            uuid = uuid ?: UUID.randomUUID(),
            name = name,
            version = version,
            runnerUUID = runnerUUID,
            creationTime = Clock.System.now()
        )

        val runner = runnerRepository.getRunner(runnerUUID) ?: return Failure.AuxiliaryResourceNotFound(runnerUUID)

        try {
            runner.initializeServer(server).ifFalse { return Failure.Unknown() }
        } catch (e: IllegalArgumentException) {
            return Failure.AlreadyExists(server.uuid)
        }

        val addSuccess = runCatching { serverRepository.addServer(server) }.getOrElse { false }
        if (addSuccess) {
            return Success(server)
        } else {
            runCatching { runner.removeServer(server) }
                .getOrElse { false }
                .ifFalse { logger.warn("Failed to clean up resources for ${server.uuid} persistence failure.") }

            return Failure.AlreadyExists(server.uuid)
        }
    }

    override suspend fun getServer(uuid: UUID): APIResult<MinecraftServer> =
        serverRepository.getServer(uuid)
            ?.let { Success(it) }
            ?: Failure.MainResourceNotFound(uuid)

    override suspend fun setServer(uuid: UUID, name: String, version: MinecraftVersion, runnerUUID: UUID): APIResult<MinecraftServer> {
        val newServer = MinecraftServer(
            uuid = uuid,
            name = name,
            version = version,
            runnerUUID = runnerUUID,
            creationTime = Clock.System.now()
        )

        serverRepository.saveServer(newServer)

        return Success(newServer)
    }

    override suspend fun updateServer(uuid: UUID, name: String?): APIResult<Unit> {
        // TODO: CONCURRENCY CONTROL/TRANSACTION MANAGEMENT

        val server = serverRepository.getServer(uuid) ?: return Failure.MainResourceNotFound(uuid)

        name?.let { server.name = it }

        serverRepository.saveServer(server)
        return Success()
    }

    override suspend fun deleteServer(uuid: UUID): APIResult<Unit> { // TODO: Ensure all environments removed
        logger.trace("Getting server with uuid {}", uuid)
        val server = serverRepository.getServer(uuid) ?: return Failure.MainResourceNotFound(uuid)
        logger.trace("Getting runner with uuid {}", uuid)
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return Failure.AuxiliaryResourceNotFound(server.runnerUUID)
        logger.trace("Removing server with uuid {} from runner with uuid {}", uuid, runner.uuid)
        val removeFromRunnerSuccess = runner.removeServer(server)
        logger.trace("Removing server with uuid {} from runner with uuid {} {}", uuid, runner.uuid, if (removeFromRunnerSuccess) "SUCCEEDED" else "FAILED")
        if (!removeFromRunnerSuccess) {
            logger.warn("Failed to remove server {} from runner {}", uuid, server.runnerUUID)
            return Failure.Unknown() // Early exit; don't remove it from the database unless it's been fully cleaned up. TODO: some better transactionality?
        }
        logger.trace("Removing server with uuid {} from server repository", uuid)
        val removeFromRepositorySuccess = serverRepository.removeServer(uuid)
        logger.trace("Removing server with uuid {} from server repository {}", uuid, if (removeFromRepositorySuccess) "SUCCEEDED" else "FAILED")
        if (!removeFromRepositorySuccess) {
            logger.warn("Failed to remove server {} from repository", uuid)
            return Failure.Unknown()
        }

        return Success()
    }

    override suspend fun getAllRunners(): APIResult<List<MinecraftServerRunner>> =
        Success(runnerRepository.getAllRunners())

    override suspend fun getRunner(uuid: UUID): APIResult<MinecraftServerRunner> =
        runnerRepository.getRunner(uuid)?.let(::Success) ?: Failure.MainResourceNotFound(uuid)

    override suspend fun getAllCurrentRuns(runnerUUID: UUID): APIResult<List<MinecraftServerCurrentRun>> {
        val runner =
            runnerRepository.getRunner(runnerUUID).ifNull {
                logger.trace("Could not find runner")
                return Failure.MainResourceNotFound(runnerUUID)
            }

        return Success(runner.getAllCurrentRuns())
    }

    override suspend fun createCurrentRun(serverUUID: UUID, environment: MinecraftServerRuntimeEnvironment): APIResult<MinecraftServerCurrentRun> {
        val server = serverRepository.getServer(serverUUID).ifNull {
            logger.trace("Couldn't find server for UUID {}", serverUUID)
            return Failure.MainResourceNotFound(serverUUID)
        }

        val runner = runnerRepository.getRunner(server.runnerUUID).ifNull {
            logger.trace("Couldn't find runner for UUID {}.runnerUUID", server)
            return Failure.AuxiliaryResourceNotFound(server.runnerUUID)
        }

        val existingRun = runner.getCurrentRun(serverUUID)
        if (existingRun != null) { // TODO: possible concurrency issues if another run starts before this one?
            logger.trace("Cannot create current run because server {} is already running", serverUUID)
            return Failure.AlreadyExists(existingRun.uuid)
        }

        return runner.runServer(server, environment)?.let(::Success) ?: return Failure.Unknown()
    }

    override suspend fun getCurrentRun(runnerUUID: UUID, runUUID: UUID): APIResult<MinecraftServerCurrentRun> {
        val runner = runnerRepository.getRunner(runnerUUID) ?: return Failure.AuxiliaryResourceNotFound(runnerUUID)
        val run = runner.getCurrentRun(runUUID) ?: return Failure.MainResourceNotFound(runUUID)

        return Success(run)
    }

    override suspend fun getCurrentRunByServer(serverUUID: UUID): APIResult<MinecraftServerCurrentRun> {
        val runner: MinecraftServerRunner = getRunnerByServer(serverUUID).orElse { return it }
        val run = runner.getCurrentRunByServer(serverUUID) ?: return Failure.MainResourceNotFound(null)

        return Success(run)
    }

    override suspend fun stopCurrentRun(runnerUUID: UUID, runUUID: UUID): APIResult<Unit> {
        logger.info("Stopping current run $runUUID")
        val runner = runnerRepository.getRunner(runnerUUID) ?: return Failure.AuxiliaryResourceNotFound(runnerUUID)

        val stopRunSuccess = try {
            runner.stopRun(runUUID)
        } catch (e: IllegalArgumentException) {
            return Failure.MainResourceNotFound(runUUID)
        }

        return if (stopRunSuccess) Success() else Failure.Unknown()
    }

    override suspend fun stopCurrentRunByServer(serverUUID: UUID): APIResult<Unit> {
        logger.info("Stopping current run of server $serverUUID")
        val server = serverRepository.getServer(serverUUID) ?: return Failure.MainResourceNotFound(serverUUID)
        val runner = runnerRepository.getRunner(server.runnerUUID) ?: return Failure.AuxiliaryResourceNotFound(server.runnerUUID)

        val stopSuccess = runner.stopRunByServer(serverUUID)

        return if (stopSuccess) Success() else Failure.Unknown()
    }

    override suspend fun stopAllCurrentRuns(runnerUUID: UUID): APIResult<Unit> {
        logger.info("Stopping all current runs for runner $runnerUUID")
        val runner = runnerRepository.getRunner(runnerUUID) ?: return Failure.MainResourceNotFound(runnerUUID)

        val stopSuccess = runner.stopAllRuns()

        return if (stopSuccess) Success() else Failure.Unknown()
    }

    override suspend fun getAllPastRuns(serverUUID: UUID): APIResult<List<MinecraftServerPastRun>> =
        pastRunRepository.getAllPastRuns(serverUUID).let(::Success)

    override suspend fun getPastRun(runUUID: UUID): APIResult<MinecraftServerPastRun> =
        pastRunRepository.getPastRun(runUUID)?.let(::Success) ?: Failure.MainResourceNotFound(runUUID)

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun getCurrentUserLoginInfo(): APIResult<UserLoginInfo> =
        getCurrentUserLoginInfoOrNull()?.let(::Success) ?: Failure.Unknown()

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun deleteCurrentUser(): APIResult<Unit> {
        return Success() // Users aren't actually stored at this point
    }

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun getCurrentUserPreferences(): APIResult<UserPreferences> {
        val loginInfo = getCurrentUserLoginInfo().orElse { return it }

        return getUserPreferences(loginInfo.userId)?.let(::Success) ?: Failure.MainResourceNotFound(null)
    }

    private suspend fun getUserPreferences(userId: UserID): UserPreferences? =
        userPreferencesRepository.getUserPreferencesOrSetDefaultOrNull(userId = userId)

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun updateCurrentUserPreferences(sortStrategy: UserPreferences.SortStrategy?): APIResult<Unit> {
        val loginInfo = getCurrentUserLoginInfo().orElse { return it }

        val updateSuccess = try {
            updateUserPreferences(loginInfo.userId, sortStrategy)
        } catch (e: IllegalArgumentException) {
            return Failure.InvalidValue(sortStrategy)
        }

        return if (updateSuccess) Success() else Failure.Unknown()
    }

    /**
     * Updates a user's preferences.
     * @param userId the user whose preferences to update
     * @param sortStrategy their new sort strategy, or null if not updating sort strategy
     * @throws IllegalArgumentException if this update would create an invalid user preferences state
     */
    private suspend fun updateUserPreferences(userId: UserID, sortStrategy: UserPreferences.SortStrategy?): Boolean {
        val oldPreferences = userPreferencesRepository.getUserPreferencesOrSetDefaultOrNull(userId = userId) ?: return false

        val preferencesModel = UserPreferencesAPIModel(oldPreferences)
        sortStrategy?.let { preferencesModel.serverSortStrategy = it }
        val newPreferences = preferencesModel.toUserPreferences() ?: throw IllegalArgumentException("Invalid preferences state $preferencesModel")

        return userPreferencesRepository.setUserPreferences(userId = userId, preferences = newPreferences)
    }

    context(PipelineContext<*, ApplicationCall>)
    override suspend fun deleteCurrentUserPreferences(): APIResult<Unit> {
        val userLoginInfo = getCurrentUserLoginInfoOrNull() ?: return Failure.Unknown()

        val deletionSuccess = userPreferencesRepository.deleteUserPreferences(userLoginInfo.userId)

        return if (deletionSuccess) Success() else Failure.Unknown()
    }

    context(PipelineContext<*, ApplicationCall>)
    private fun getCurrentUserLoginInfoOrNull(): UserLoginInfo? =
        call.principal<UserLoginInfo>()

    /**
     * Finds the runner for a particular server.
     * Assumes the main resource is not the server or the runner.
     */
    private fun getRunnerByServer(serverUUID: UUID): APIResult<MinecraftServerRunner> {
        val server = serverRepository.getServer(serverUUID).ifNull {
            logger.trace("Couldn't find server for UUID {}", serverUUID)
            return Failure.AuxiliaryResourceNotFound(serverUUID)
        }

        val runner = runnerRepository.getRunner(server.runnerUUID).ifNull {
            logger.trace("Couldn't find runner for UUID {}.runnerUUID", server)
            return Failure.AuxiliaryResourceNotFound(server.runnerUUID)
        }

        return Success(runner)
    }

    private val serverRepository: MinecraftServerRepository by inject()
    private val runnerRepository: MinecraftServerRunnerRepository by inject()
    private val pastRunRepository: MinecraftServerPastRunRepository by inject()
    private val userPreferencesRepository: UserPreferencesRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)
}