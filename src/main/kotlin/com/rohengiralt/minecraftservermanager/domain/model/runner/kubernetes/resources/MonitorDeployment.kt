package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.resources

import com.rohengiralt.minecraftservermanager.util.kubernetes.*
import io.kubernetes.client.custom.IntOrString
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Deployment
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim
import io.kubernetes.client.openapi.models.V1Secret
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A Kubernetes deployment for creating a monitor microservice
 * @param id a unique ID identifying this monitor.
 */
fun monitorDeployment(
    id: Int,
    serverName: String,
    minSpaceMB: Int,
    maxSpaceMB: Int
): V1Deployment {
    val monitorName = monitorName(id)
    val appLabel = monitorLabel(id)
    val httpPort = 8080
    val minecraftPort = 8080

    return deployment {
        metadata {
            name = "$monitorName-deployment"
            labels = appLabel
        }

        spec {
            replicas = 1
            selector {
                matchLabels(appLabel)
            }

            template {
                metadata {
                    labels = appLabel
                }

                spec {
                    containers {
                        container {
                            name = "$monitorName-container"
                            image = "stapledbattery/minecraftservermanager-monitor"
                            imagePullPolicy = "Always"

                            volumeMounts {
                                volumeMount {
                                    name = "home"
                                    mountPath = "/monitor"
                                }
                            }

                            env {
                                `var` { name = "minSpaceMB"; value = minSpaceMB.toString() }
                                `var` { name = "maxSpaceMB"; value = maxSpaceMB.toString() }
                                `var` { name = "name"; value = serverName }
                                `var` { name = "port"; value = httpPort.toString() }
                                `var` {
                                    name = "token"
                                    valueFrom {
                                        secretKeyRef {
                                            name = monitorName
                                            key = "token"
                                        }
                                    }
                                }
                            }

                            ports {
                                port { containerPort = httpPort; name = monitorHttpContainerPortName() }
                                port { containerPort = minecraftPort; name = monitorMinecraftContainerPortName() }
                            }
                        }
                    }

                    volumes {
                        volume {
                            name = "home"
                            persistentVolumeClaim {
                                claimName = "$monitorName-pvc"
                            }
                        }
                    }

                    restartPolicy = "Always"
                }
            }
        }
    }
}

/**
 * The service used to expose the monitor
 * @param monitorID a unique ID identifying the monitor
 * @param httpPort the port to expose for http and websocket traffic
 */
fun monitorService(
    monitorID: Int,
    httpPort: Int,
) = service {
    metadata {
        name = monitorName(monitorID)
    }

    spec {
        type = "ClusterIP"
        selector = monitorLabel(monitorID)
        ports {
            port {
                name = "http"
                protocol = "TCP"
                port = httpPort
                targetPort = IntOrString(monitorHttpContainerPortName())
            }
        }
    }
}
/**
 * The PVC used by the monitor to store its data
 * @param monitorID a unique ID identifying the monitor
 */
fun monitorPVC(monitorID: Int, storageMiB: Int): V1PersistentVolumeClaim = persistentVolumeClaim {
    metadata {
        name = "msm-monitor$monitorID-pvc"
    }
    spec {
        accessModes = listOf("ReadWriteOnce")
        storageClassName = "local-path"
        resources {
            requests = mapOf(
                "storage" to Quantity("${storageMiB}Mi"),
            )
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun monitorSecret(monitorID: Int, token: String): V1Secret = secret {
    metadata {
        name = monitorName(monitorID)
    }
    type = "Opaque"
    data = mutableMapOf("token" to Base64.encode(token.toByteArray()).toByteArray())
}

/**
 * A label used for identifying a monitor instance
 */
fun monitorLabel(monitorID: Int): Map<String, String> =
    mapOf("app" to monitorName(monitorID))

fun monitorHttpContainerPortName(): String = "http"

fun monitorMinecraftContainerPortName(): String = "minecraft"

/**
 * The name for a monitor with id [monitorID].
 * Also used as the prefix for various resources regarding the monitor
 */
private fun monitorName(monitorID: Int): String =
    "msm-monitor$monitorID"