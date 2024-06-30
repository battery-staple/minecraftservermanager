package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import io.ktor.utils.io.errors.*
import java.util.*

interface MinecraftServerPastRunRepository {
    /**
     * Gets a single past run by its UUID
     * @return the run with UUID [uuid], or null if none found
     * @throws IOException if retrieval fails
     */
    fun getPastRun(uuid: UUID): MinecraftServerPastRun?

    /**
     * Gets all of the past runs stored in this repository, optionally filtered by server or runner UUID.
     * @param serverUUID the server to filter by, or none if not to filter by server
     * @param runnerUUID the runner to filter by, or none if not to filter by runner
     * @throws IOException if retrieval fails
     */
    fun getAllPastRuns(serverUUID: UUID? = null, runnerUUID: UUID? = null): List<MinecraftServerPastRun>

    /**
     * Stores a past run into the repository, possibly overwriting another run with the same UUID.
     * @throws IOException if saving fails
     */
    fun savePastRun(run: MinecraftServerPastRun)

    /**
     * Stores several past runs into the repository, possibly overwriting others with the same UUID.
     * @throws IOException if saving fails
     */
    fun savePastRuns(runs: Iterable<MinecraftServerPastRun>)
}