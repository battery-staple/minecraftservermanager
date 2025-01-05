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
import com.rohengiralt.minecraftservermanager.domain.repository.DatabaseKubernetesEnvironmentRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MinecraftServerRepository
import com.rohengiralt.minecraftservermanager.domain.repository.MonitorTokenRepository
import com.rohengiralt.minecraftservermanager.util.extensions.map.contains
import com.rohengiralt.minecraftservermanager.util.kubernetes.scaleDeployment
import com.rohengiralt.shared.serverProcess.MinecraftServerProcess
import com.uchuhimo.konf.ConfigSpec
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.AppsV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Runs Minecraft Servers by deploying them to their own container in a Kubernetes cluster
 */
class KubernetesRunner(uuid: RunnerUUID) : AbstractMinecraftServerRunner<KubernetesEnvironment>(
    uuid = uuid,
    name = "Kubernetes",
    environments = getKoin().get<DatabaseKubernetesEnvironmentRepository>()
), KoinComponent {

    override val domain: String get() = kubeRunnerConfig[KubeRunnerSpec.domain]

    override suspend fun prepareEnvironment(server: MinecraftServer): KubernetesEnvironment? { // TODO: delete all resources if creation of any fails
        val monitorID = getMonitorID(server.uuid)
        val monitorToken = tokens.generateTokenForServer(server.uuid)

        val service = monitorService(monitorID, httpPort = MONITOR_HTTP_PORT)
        logger.debug("Creating service ${service.metadata.name} for server ${server.name}")
        try {
            val serviceResponse = kubeCore.createNamespacedService("default", service).execute()
            logger.debug("Created service ${serviceResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create service ${service.metadata.name} for server ${server.name}", e)
            return null
        }

        val homePVC = monitorPVC(monitorID, 128)
        logger.debug("Creating PVC ${homePVC.metadata.name} for server ${server.name}")
        try {
            val homePVCResponse = kubeCore.createNamespacedPersistentVolumeClaim("default", homePVC).execute()
            logger.debug("Created PVC ${homePVCResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create PVC ${homePVC.metadata.name} for server ${server.name}", e)
            return null
        }

        val secret = monitorSecret(monitorID, monitorToken.asString())
        logger.debug("Creating secret ${secret.metadata.name} for server ${server.name}")
        try {
            val secretResponse = kubeCore.createNamespacedSecret("default", secret).execute()
            logger.debug("Created secret ${secretResponse.metadata.name} for server ${server.name}")
        } catch (e: ApiException) {
            logger.error("Failed to create secret ${secret.metadata.name} for server ${server.name}", e)
            return null
        }

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
            return null
        }

        return KubernetesEnvironment(
            uuid = EnvironmentUUID(UUID.randomUUID()),
            runnerUUID = this.uuid,
            server = server,
            monitorToken = monitorToken,
        )
    }

    override suspend fun cleanupEnvironment(environment: KubernetesEnvironment): Boolean {
        val server = servers.getServer(environment.serverUUID)
        val serverName = server?.name ?: environment.serverUUID // Fail safely if server cannot be retrieved for whatever reason
        val monitorID = getMonitorID(environment.serverUUID)
        val monitorToken = tokens.generateTokenForServer(environment.serverUUID)

        val deploymentName = monitorName(monitorID)
        logger.debug("Deleting deployment {} for server {}", deploymentName, serverName)
        try {
            /*val deploymentResponse = */kubeApps.deleteNamespacedDeployment(deploymentName, "default").execute()
            logger.debug("Deleted deployment {} for server {}", deploymentName, serverName)
        } catch (e: ApiException) {
            if (e.code == 404) {
                logger.error("Deployment {} for server {} was already deleted. Skipping deletion.", deploymentName, serverName)
            } else {
                logger.error("Failed to create deployment {} for server {}", deploymentName, serverName, e)
                return false
            }
        }

        val service = monitorService(monitorID, httpPort = MONITOR_HTTP_PORT)
        logger.debug("Removing service {} for server {}", service.metadata.name, serverName)
        try {
            val serviceResponse = kubeCore.deleteNamespacedService(service.metadata.name, "default").execute()
            logger.debug("Deleted service {} for server {}", serviceResponse.metadata.name, serverName)
        } catch (e: ApiException) {
            if (e.code == 404) {
                logger.error("Service {} for server {} was already deleted. Skipping deletion.", service.metadata.name, serverName)
            } else {
                logger.error("Failed to delete service ${service.metadata.name} for server $serverName", e)
                return false
            }
        }

        val homePVC = monitorPVC(monitorID, 128)
        logger.debug("Deleting PVC {} for server {}", homePVC.metadata.name, serverName)
        try {
            val homePVCResponse = kubeCore.deleteNamespacedPersistentVolumeClaim(homePVC.metadata.name, "default").execute()
            logger.debug("Deleted PVC {} for server {}", homePVCResponse.metadata.name, serverName)
        } catch (e: ApiException) {
            if (e.code == 404) {
                logger.error("PVC {} for server {} was already deleted. Skipping deletion.", homePVC.metadata.name, serverName)
            } else {
                logger.error("Failed to delete PVC ${homePVC.metadata.name} for server $serverName", e)
                return false
            }
        }

        val secret = monitorSecret(monitorID, monitorToken.asString())
        logger.debug("Deleting secret {} for server {}", secret.metadata.name, serverName)
        try {
            /*val secretResponse = */kubeCore.deleteNamespacedSecret(secret.metadata.name, "default").execute()
            logger.debug("Deleted secret {}", secret.metadata.name)
        } catch (e: ApiException) {
            if (e.code == 404) {
                logger.error("Secret {} for server {} was already deleted. Skipping deletion.", secret.metadata.name, serverName)
            } else {
                logger.error("Failed to create secret {} for server {}", secret.metadata.name, serverName, e)
                return false
            }
        }

        return true
    }

    override suspend fun getLog(runRecord: MinecraftServerCurrentRunRecord): List<LogEntry>? {
        TODO("Not yet implemented")
    }

    private val tokens: MonitorTokenRepository by inject()

    private val kubeCore: CoreV1Api by inject()
    private val kubeApps: AppsV1Api by inject()
    private val servers: MinecraftServerRepository by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        fun getMonitorID(server: ServerUUID) =
            server.value.toString()
    }
}

class KubernetesEnvironment private constructor(
    override val uuid: EnvironmentUUID,
    override val serverUUID: ServerUUID,
    override val runnerUUID: RunnerUUID,
    internal val monitorToken: MonitorToken, // Not private because used in repository
    private val monitorID: String,
    initialProcess: MinecraftServerProcess?
) : MinecraftServerEnvironment, KoinComponent {
    override suspend fun runServer(port: Port, maxHeapSizeMB: UInt, minHeapSizeMB: UInt): MinecraftServerProcess? = mutex.withLock {
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
        val serviceSuccess = configureMinecraftService(port, server)
        if (!serviceSuccess) return null

        logger.trace("Scaling up the monitor for server {} ({})", server.name, server.uuid)
        val monitorSuccess = kubeApps.scaleDeployment(monitorName(monitorID), "default", replicas = 1)
        if (!monitorSuccess) return null

        logger.trace("Creating pod process")
        val newConnection = DeploymentProcess(
            server = server,
            hostname = monitorName(monitorID),
            port = MONITOR_HTTP_PORT,
            token = monitorToken
        )
        newConnection.connect(restartOnFailure = true)

        _currentProcess.update { newConnection }

        coroutineScope.launch {
            // Wait for pod to end (and/or be replaced)
            newConnection.waitForExit()
            _currentProcess.compareAndSet(newConnection, null)
        }

        return newConnection
    }

    /**
     * Configures the Minecraft service for port [port] to point to [server]'s pod.
     * @return true if the service is now correctly set
     */
    private fun configureMinecraftService(
        port: Port,
        server: MinecraftServer
    ): Boolean {
        val service = monitorMinecraftService("msm-minecraft-1", monitorID, minecraftPort = port.number.toInt())
        logger.debug("Configuring minecraft service ${service.metadata.name} for server ${server.name}")
        try {
            val serviceResponse = kubeCore.replaceNamespacedService(service.metadata.name, "default", service).execute()
            logger.debug("Configured minecraft service ${serviceResponse.metadata.name} for server ${server.name}")
            return true
        } catch (e: ApiException) {
            logger.error("Failed to configure service ${service.metadata.name} for server ${server.name}", e)
            return false
        }
    }

    private val _currentProcess: MutableStateFlow<MinecraftServerProcess?> = MutableStateFlow(initialProcess)
    override val currentProcess: StateFlow<MinecraftServerProcess?> = _currentProcess.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val kubeApps: AppsV1Api by inject()

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object : KoinComponent {
        suspend operator fun invoke(
            uuid: EnvironmentUUID,
            runnerUUID: RunnerUUID,
            server: MinecraftServer,
            monitorToken: MonitorToken,
        ): KubernetesEnvironment {
            val monitorID = KubernetesRunner.getMonitorID(server.uuid)

            return KubernetesEnvironment(
                uuid = uuid,
                serverUUID = server.uuid,
                runnerUUID = runnerUUID,
                monitorToken = monitorToken,
                monitorID = monitorID,
                initialProcess = initialProcess(monitorID, server, monitorToken)
            )
        }

        private suspend fun initialProcess(
            monitorID: String,
            server: MinecraftServer,
            monitorToken: MonitorToken
        ): MinecraftServerProcess? {
            val monitorLabel = monitorLabel(monitorID)

            val isDeploymentRunning = try {
                val pod = kubeCore.listNamespacedPod("default").execute()
                pod.items.any { monitorLabel in it.metadata.labels }
            } catch (e: ApiException) { false }

            if (!isDeploymentRunning) {
                return null
            }

            val initialProcess = DeploymentProcess(
                server = server,
                hostname = monitorName(monitorID),
                port = MONITOR_HTTP_PORT,
                token = monitorToken
            )

            try {
                initialProcess.connect(restartOnFailure = false)
            } catch (e: DeploymentProcess.ConnectionTimeoutException) {
                return null
            }

            return initialProcess
        }

        private val servers: MinecraftServerRepository by inject()
        private val kubeCore: CoreV1Api by inject()
    }
}

private const val MONITOR_HTTP_PORT = 8080

private val kubeRunnerConfig = com.uchuhimo.konf.Config { addSpec(KubeRunnerSpec) }
    .from.env()

private object KubeRunnerSpec : ConfigSpec() {
    val domain by required<String>()
}