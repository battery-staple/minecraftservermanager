package com.rohengiralt.minecraftservermanager.domain.model

import java.util.*

interface MinecraftServerPastRunRepository {
    fun getPastRun(uuid: UUID): MinecraftServerPastRun?
    fun getAllPastRuns(serverUUID: UUID? = null, runnerUUID: UUID? = null): List<MinecraftServerPastRun>
    fun savePastRun(run: MinecraftServerPastRun): Boolean
}