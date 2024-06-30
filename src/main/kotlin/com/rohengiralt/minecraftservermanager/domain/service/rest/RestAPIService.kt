package com.rohengiralt.minecraftservermanager.domain.service.rest

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRun
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftVersion
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Failure
import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService.APIResult.Success
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.user.preferences.UserPreferences
import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import java.util.*

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
    suspend fun createServer(uuid: UUID? = null, name: String, version: MinecraftVersion, runnerUUID: UUID): APIResult<MinecraftServer>
    suspend fun getServer(uuid: UUID): APIResult<MinecraftServer>
    suspend fun setServer(uuid: UUID, name: String, version: MinecraftVersion, runnerUUID: UUID): APIResult<MinecraftServer>
    suspend fun updateServer(uuid: UUID, name: String? = null): APIResult<Unit>
    suspend fun deleteServer(uuid: UUID): APIResult<Unit>

    suspend fun getAllRunners(): APIResult<List<MinecraftServerRunner>>
    suspend fun getRunner(uuid: UUID): APIResult<MinecraftServerRunner>
    suspend fun getAllCurrentRuns(runnerUUID: UUID): APIResult<List<MinecraftServerCurrentRun>>
    suspend fun createCurrentRun(serverUUID: UUID, environment: MinecraftServerEnvironment): APIResult<MinecraftServerCurrentRun>
    suspend fun getCurrentRun(runnerUUID: UUID, runUUID: UUID): APIResult<MinecraftServerCurrentRun>
    suspend fun getCurrentRunByServer(serverUUID: UUID): APIResult<MinecraftServerCurrentRun>
    suspend fun stopCurrentRun(runnerUUID: UUID, runUUID: UUID): APIResult<Unit>
    suspend fun stopCurrentRunByServer(serverUUID: UUID): APIResult<Unit>
    suspend fun stopAllCurrentRuns(runnerUUID: UUID): APIResult<Unit>

    suspend fun getAllPastRuns(serverUUID: UUID): APIResult<List<MinecraftServerPastRun>>
    suspend fun getPastRun(serverUUID: UUID, runUUID: UUID): APIResult<MinecraftServerPastRun>

    context(PipelineContext<*, ApplicationCall>)
    suspend fun getCurrentUserLoginInfo(): UserLoginInfo?

    context(PipelineContext<*, ApplicationCall>)
    suspend fun deleteCurrentUser(): Boolean

    context(PipelineContext<*, ApplicationCall>)
    suspend fun getCurrentUserPreferences(): UserPreferences?
    context(PipelineContext<*, ApplicationCall>)
    suspend fun updateCurrentUserPreferences(sortStrategy: UserPreferences.SortStrategy? = null): Boolean

    context(PipelineContext<*, ApplicationCall>)
    suspend fun deleteCurrentUserPreferences(): Boolean

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
            data class MainResourceNotFound(val resourceUUID: UUID?) : Failure

            /**
             * Represents a failure due to any resource other than the main one not being found.
             * @param resourceUUID the UUID of the resource not found
             */
            data class AuxiliaryResourceNotFound(val resourceUUID: UUID) : Failure

            /**
             * Represents a failure due to attempting to create a resource that already exists
             */
            data class AlreadyExists(val resourceUUID: UUID) : Failure

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

