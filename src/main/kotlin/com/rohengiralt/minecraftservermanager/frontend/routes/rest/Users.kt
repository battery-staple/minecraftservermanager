package com.rohengiralt.minecraftservermanager.frontend.routes.rest

import com.rohengiralt.minecraftservermanager.domain.service.rest.RestAPIService
import com.rohengiralt.minecraftservermanager.frontend.model.UserLoginInfoAPIModel
import com.rohengiralt.minecraftservermanager.frontend.model.UserPreferencesAPIModel
import com.rohengiralt.minecraftservermanager.frontend.routes.orThrow
import com.rohengiralt.minecraftservermanager.user.UserLoginInfo
import com.rohengiralt.minecraftservermanager.util.routes.receiveSerializable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.usersRoute() {
    route("/current") { // users should not be able to access other users
        val restApiService: RestAPIService by this@usersRoute.inject()

        get {
            call.application.environment.log.info("Getting user login info with id ${call.principal<UserLoginInfo>()?.userId}")
            val user = restApiService.getCurrentUserLoginInfo().orThrow()

            call.respond(UserLoginInfoAPIModel(user))
        }

        delete {
            call.application.environment.log.info("Deleting user with id: ${call.principal<UserLoginInfo>()?.userId}")
            restApiService.deleteCurrentUser().orThrow()
            val deleted = restApiService.deleteCurrentUserPreferences().orThrow()

            if (deleted != null) {
                call.respond(HttpStatusCode.OK, deleted)
            } else {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/preferences") {
            get {
                call.application.environment.log.info("Getting user preferences for user with id ${call.principal<UserLoginInfo>()?.userId}")

                val preferences = restApiService
                    .getCurrentUserPreferences().orThrow()
                    .let(::UserPreferencesAPIModel)

                call.respond(preferences)
            }

            patch {
                call.application.environment.log.info("Patching user preferences for user with id ${call.principal<UserLoginInfo>()?.userId}")
                val userPreferencesAPIModel: UserPreferencesAPIModel = call.receiveSerializable()

                val preferences = restApiService.updateCurrentUserPreferences(
                    sortStrategy = userPreferencesAPIModel.serverSortStrategy,
                ).orThrow().let(::UserPreferencesAPIModel)

                call.respond(HttpStatusCode.OK, preferences)
            }
        }
    }
}