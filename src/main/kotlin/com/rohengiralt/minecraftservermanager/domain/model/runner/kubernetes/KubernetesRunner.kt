package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes

import com.rohengiralt.minecraftservermanager.domain.model.run.LogEntry
import com.rohengiralt.minecraftservermanager.domain.model.run.MinecraftServerCurrentRunRecord
import com.rohengiralt.minecraftservermanager.domain.model.runner.AbstractMinecraftServerRunner
import com.rohengiralt.minecraftservermanager.domain.model.runner.EnvironmentUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.MinecraftServerEnvironment
import com.rohengiralt.minecraftservermanager.domain.model.runner.RunnerUUID
import com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.resources.*
import com.rohengiralt.minecraftservermanager.domain.model.server.MinecraftServer
import com.rohengiralt.minecraftservermanager.domain.model.server.Port
import com.rohengiralt.minecraftservermanager.domain.model.server.ServerUUID
import com.rohengiralt.minecraftservermanager.domain.repository.EnvironmentRepository
import com.rohengiralt.minecraftservermanager.domain.repository.InMemoryEnvironmentRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.util.extensions.httpClient.logger
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.uchuhimo.konf.ConfigSpec
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.AppsV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.Config
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Runs Minecraft Servers by deploying them to their own container in a Kubernetes cluster
 */
class KubernetesRunner(uuid: RunnerUUID) : AbstractMinecraftServerRunner<KubernetesEnvironment>(
    uuid = uuid,
    name = "Kubernetes"
) {
    override val domain: String get() = kubeRunnerConfig[KubeRunnerSpec.domain]

    override suspend fun prepareEnvironment(server: MinecraftServer): KubernetesEnvironment { // TODO: delete all resources if creation of any fails
        val monitorID = 1

        val deployment = monitorDeployment(
            id = monitorID,
            serverName = server.name,
            minSpaceMB = 512,
            maxSpaceMB = 2048
        )
        logger.debug("Creating deployment ${deployment.metadata.name} for server ${server.name}")
        try {
            val deploymentResponse = kubeApps.createNamespacedDeployment("default", deployment).execute()
            logger.debug("Created deployment ${deploymentResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create deployment ${deployment.metadata.name} for server ${server.name}", e)
        }

        val service = monitorService(monitorID, httpPort = MONITOR_HTTP_PORT)
        logger.debug("Creating service ${service.metadata.name} for server ${server.name}")
        try {
            val serviceResponse = kubeCore.createNamespacedService("default", service).execute()
            logger.debug("Created service ${serviceResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create service ${service.metadata.name} for server ${server.name}", e)
        }

        val homePVC = monitorPVC(monitorID, 128)
        logger.debug("Creating PVC ${homePVC.metadata.name} for server ${server.name}")
        try {
            val homePVCResponse = kubeCore.createNamespacedPersistentVolumeClaim("default", homePVC).execute()
            logger.debug("Created PersistentVolumeClaim ${homePVCResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create PVC ${homePVC.metadata.name} for server ${server.name}", e)
        }

        val secret = monitorSecret(monitorID, "asdf") // TODO: real token!!
        logger.debug("Creating secret ${secret.metadata.name} for server ${server.name}")
        try {
            val secretResponse = kubeCore.createNamespacedSecret("default", secret).execute()
            logger.debug("Created secret ${secretResponse.metadata.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create secret ${secret.metadata.name} for server ${server.name}", e)
        }

        return KubernetesEnvironment(
            uuid = EnvironmentUUID(UUID.randomUUID()),
            serverUUID = server.uuid,
            runnerUUID = this.uuid,
            monitorID = monitorID,
            monitorToken = "asdf",
            kubeCore = kubeCore,
            kubeApps = kubeApps
        )
    }

    override suspend fun cleanupEnvironment(environment: MinecraftServerEnvironment): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun getLog(runRecord: MinecraftServerCurrentRunRecord): List<LogEntry>? {
        TODO("Not yet implemented")
    }

    override val environments: EnvironmentRepository<KubernetesEnvironment> = InMemoryEnvironmentRepository() // TODO: PERSISTENT!!

    private val kubeClient: ApiClient = Config.defaultClient()
    private val kubeCore = CoreV1Api(kubeClient)
    private val kubeApps = AppsV1Api(kubeClient)

    private val logger = LoggerFactory.getLogger(this::class.java)
}

class KubernetesEnvironment(
    override val uuid: EnvironmentUUID,
    override val serverUUID: ServerUUID,
    override val runnerUUID: RunnerUUID,
    private val monitorID: Int,
    private val monitorToken: String,
    private val kubeCore: CoreV1Api,
    private val kubeApps: AppsV1Api
) : MinecraftServerEnvironment, KoinComponent {
    override suspend fun runServer(port: Port, maxHeapSizeMB: UInt, minHeapSizeMB: UInt): MinecraftServerProcess? {
        if (port.number != 25565u.toUShort()) {
            logger.error("Attempted to run server on unsupported port {} in environment {}", port.number, uuid) // TODO: Remove this restriction!
            return null
        }

        val server = servers.getServer(serverUUID)
        if (server == null) {
            logger.error("Attempted to run deleted server {}", serverUUID)
            return null
        }

        logger.trace("Configuring service for port {} to point to server {} ({})", port, server.name, server.uuid)
        configureMinecraftService(port, server)

        logger.trace("Scaling up the monitor for server {} ({})", server.name, server.uuid)
        scaleMonitor(monitorID, replicas = 1)

        return MinecraftServerPod(
            serverName = server.name,
            hostname = monitorName(monitorID),
            port = MONITOR_HTTP_PORT,
            token = monitorToken
        )
    }

    private fun scaleMonitor(monitorID: Int, replicas: Int) {
        // PATCH seems to be broken on the API client currently, so GET and PUT instead. TODO: revisit this later
        val scale = kubeApps.readNamespacedDeploymentScale("msm-monitor$monitorID-deployment", "default").execute()
        kubeApps.replaceNamespacedDeploymentScale("msm-monitor$monitorID-deployment", "default", scale.apply {
            spec.replicas = replicas
        }).execute()
    }

    private fun configureMinecraftService(
        port: Port,
        server: MinecraftServer
    ) {
        val service = monitorMinecraftService("msm-minecraft-1", monitorID, minecraftPort = port.number.toInt())
        logger.debug("Configuring minecraft service ${service.metadata.name} for server ${server.name}")
        try {
            val serviceResponse = kubeCore.createNamespacedService("default", service).execute()
            logger.debug("Configuring minecraft service ${serviceResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to configure service ${service.metadata.name} for server ${server.name}", e)
        }
    }

    override val currentProcess: StateFlow<MinecraftServerProcess?>
        get() = TODO("Not yet implemented")

    private val servers: MinecraftServerRepository by inject()
}

private const val MONITOR_HTTP_PORT = 8080

private val kubeRunnerConfig = com.uchuhimo.konf.Config { addSpec(KubeRunnerSpec) }
    .from.env()

private object KubeRunnerSpec : ConfigSpec() {
    val domain by required<String>()
}