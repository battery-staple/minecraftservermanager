package com.rohengiralt.minecraftservermanager.domain.service.rest

import com.rohengiralt.minecraftservermanager.domain.ResourceUUID
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.run.RunUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerRuntimeEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Failure
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Success
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferences
import io.ktor.server.application.*
import io.ktor.util.pipeline.*

/**
 * An interface defining all the methods used by the Rest API.
 * This interface provides a bridge between the domain and the frontend code
 * directly handling client connections.
 * The frontend should delegate immediately to an implementation of this interface.
 *
 * All methods in this interface should return a [APIResult],
 * which can be relatively easily mapped into an HTTP response.
 */
interface RestAPIService {
    suspend fun getAllServers(): APIResult<List<MinecraftServer>>
    suspend fun createServer(uuid: ServerUUID? = null, name: String, version: MinecraftVersion, runnerUUID: RunnerUUID): APIResult<MinecraftServer>
    suspend fun getServer(uuid: ServerUUID): APIResult<MinecraftServer>
    suspend fun setServer(uuid: ServerUUID, name: String, version: MinecraftVersion, runnerUUID: RunnerUUID): APIResult<MinecraftServer>
    suspend fun updateServer(uuid: ServerUUID, name: String? = null): APIResult<MinecraftServer>
    suspend fun deleteServer(uuid: ServerUUID): APIResult<MinecraftServer>

    suspend fun getAllRunners(): APIResult<List<MinecraftServerRunner>>
    suspend fun getRunner(uuid: RunnerUUID): APIResult<MinecraftServerRunner>
    suspend fun getAllCurrentRuns(runnerUUID: RunnerUUID): APIResult<List<MinecraftServerCurrentRun>>
    suspend fun createCurrentRun(serverUUID: ServerUUID, environment: MinecraftServerRuntimeEnvironment): APIResult<MinecraftServerCurrentRun>
    suspend fun getCurrentRun(runnerUUID: RunnerUUID, runUUID: RunUUID): APIResult<MinecraftServerCurrentRun>
    suspend fun getCurrentRunByServer(serverUUID: ServerUUID): APIResult<MinecraftServerCurrentRun>
    suspend fun stopCurrentRun(runnerUUID: RunnerUUID, runUUID: RunUUID): APIResult<Unit>
    suspend fun stopCurrentRunByServer(uuid: ServerUUID): APIResult<Unit>
    suspend fun stopAllCurrentRuns(runnerUUID: RunnerUUID): APIResult<Unit>

    suspend fun getAllPastRuns(serverUUID: ServerUUID): APIResult<List<MinecraftServerPastRun>>
    suspend fun getPastRun(runUUID: RunUUID): APIResult<MinecraftServerPastRun>

    context(API) suspend fun getCurrentUserLoginInfo(): APIResult<UserLoginInfo>
    context(API) suspend fun deleteCurrentUser(): APIResult<Unit>

    context(API) suspend fun getCurrentUserPreferences(): APIResult<UserPreferences>
    context(API) suspend fun updateCurrentUserPreferences(sortStrategy: UserPreferences.SortStrategy? = null): APIResult<UserPreferences>
    context(API) suspend fun deleteCurrentUserPreferences(): APIResult<UserPreferences?>

    /**
     * The result of an API action.
     * May be either a [Success] or [Failure].
     * Intended to be relatively easily mappable onto an HTTP response.
     * @param T the type of the payload if successful
     */
    sealed interface APIResult<out T> {
        /**
         * Represents a successful execution of the action, potentially including some returned content.
         * If an action does not intend to return additional content, it may return a `Success<Unit>`.
         */
        data class Success<T>(val content: T) : APIResult<T>

        /**
         * Represents an unsuccessful execution of the action.
         */
        sealed interface Failure : APIResult<Nothing> {
            /**
             * Represents a failure due to the main resource not being found.
             * There should be at most one "main resource" in any API call.
             * For instance, the main resource of a `get*` method should be the resource attempting to be retrieved.
             * @param resourceUUID the UUID of the resource not found, or null if the resource's UUID is not known.
             */
            data class MainResourceNotFound(val resourceUUID: ResourceUUID?) : Failure

            /**
             * Represents a failure due to any resource other than the main one not being found.
             * @param resourceUUID the UUID of the resource not found
             */
            data class AuxiliaryResourceNotFound(val resourceUUID: ResourceUUID) : Failure

            /**
             * Represents a failure due to attempting to create a resource that already exists
             */
            data class AlreadyExists(val resourceUUID: ResourceUUID) : Failure

            /**
             * Represents a failure due to a parameter with the correct type but invalid value.
             * For instance, a negative number when a positive integer is required
             */
            data class InvalidValue(val value: Any?) : Failure

            /**
             * Represents a failure for an unknown reason.
             * @param exn optionally, the exception that caused the failure
             */
            data class Unknown(val exn: Exception? = null) : Failure
        }

        companion object {
            /**
             * Utility function for creating a `Success<Unit>`.
             */
            fun Success(): Success<Unit> = Success(Unit)
        }
    }
}

private typealias API = PipelineContext<*, ApplicationCall>