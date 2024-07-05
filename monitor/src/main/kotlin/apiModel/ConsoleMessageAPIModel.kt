package com.rohengiralt.apiModel

import com.rohengiralt.monitor.serverProcess.ServerIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ConsoleMessageAPIModel { // TODO: extract to shared, rename, and typealias in frontend
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