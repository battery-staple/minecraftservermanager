package com.rohengiralt.minecraftservermanager.domain.repository

import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerPastRun
import java.util.*

interface MinecraftServerPastRunRepository {
    fun getPastRun(uuid: UUID): MinecraftServerPastRun?
    fun getAllPastRuns(serverUUID: UUID? = null, runnerUUID: UUID? = null): List<MinecraftServerPastRun>
    fun savePastRun(run: MinecraftServerPastRun): Boolean
    fun savePastRuns(runs: Iterable<MinecraftServerPastRun>): Boolean
}