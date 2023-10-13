package com.rohengiralt.minecraftservermanager.frontend.routes.rest

import com.rohengiralt.minecraftservermanager.domain.service.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.UserLoginInfoAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.UserPreferencesAPIModel
import com.rohengiralt.minecraftservermanager.plugins.AuthorizationException
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.util.routes.receiveSerializable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.usersRoute() {
    route("/current") { // users should not be able to access other users
        val restApiService: RestAPIService by this@usersRoute.inject()

        get {
            println("Getting user login info with id ${call.principal<UserLoginInfo>()?.userId}")
            val user = restApiService.getCurrentUserLoginInfo() ?: throw AuthorizationException()

            call.respond(UserLoginInfoAPIModel(user))
        }

        delete {
            println("Deleting user with id: ${call.principal<UserLoginInfo>()?.userId}")
            val userInfoDeletionSuccess = restApiService.deleteCurrentUser()
            val userPreferencesDeletionSuccess = restApiService.deleteCurrentUserPreferences()

            if (userInfoDeletionSuccess && userPreferencesDeletionSuccess) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        route("/preferences") {
            get {
                println("Getting user preferences for user with id ${call.principal<UserLoginInfo>()?.userId}")

                val preferences = restApiService
                    .getCurrentUserPreferences()
                    ?.let(::UserPreferencesAPIModel)
                    ?: throw NotFoundException()

                call.respond(preferences)
            }

            patch {
                println("Patching user preferences for user with id ${call.principal<UserLoginInfo>()?.userId}")
                val userPreferencesAPIModel: UserPreferencesAPIModel = call.receiveSerializable()

                val success = restApiService.updateCurrentUserPreferences(
                    sortStrategy = userPreferencesAPIModel.serverSortStrategy,
                )

                if (success) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}