package com.rohengiralt.minecraftservermanager.util.extensions.logger

import kotlinx.coroutines.Job
import org.slf4j.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Logs when a job ends, along with information about the cause.
 * @param name the name of the job, to be included in the log message
 */
fun Logger.logOnEnd(job: Job, name: String) {
    job.invokeOnCompletion { cause ->
        when (cause) {
            null -> trace("Ended '$name' job normally")
            is CancellationException -> trace("Ended '$name' job due to cancellation")
            else -> trace("Ended '$name' job exceptionally", cause)
        }
    }
}
