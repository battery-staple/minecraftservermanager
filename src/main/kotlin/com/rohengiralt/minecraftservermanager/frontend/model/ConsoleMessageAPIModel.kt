package com.rohengiralt.minecraftservermanager.frontend.model

import com.rohengiralt.minecraftservermanager.domain.model.server.ServerIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ConsoleMessageAPIModel {
    abstract val text: String
    @Serializable
    @SerialName("Input")
    data class Input(override val text: String) : ConsoleMessageAPIModel()

    @Serializable
    sealed class Output : ConsoleMessageAPIModel() {
        @Serializable
        @SerialName("Log")
        data class Log(override val text: String) : Output()

        @Serializable
        @SerialName("Error")
        data class ProcessError(override val text: String) : Output()
    }

    companion object {
        fun fromServerIO(serverIO: ServerIO): ConsoleMessageAPIModel = when (serverIO) {
            is ServerIO.Output.LogMessage -> Output.Log(serverIO.text)
            is ServerIO.Output.ErrorMessage -> Output.ProcessError(serverIO.text)
            is ServerIO.Input.InputMessage -> Input(serverIO.text)
        }
    }
}