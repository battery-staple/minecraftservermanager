package com.rohengiralt.minecraftservermanager.domain.model.runner.kubernetes.resources

import com.rohengiralt.minecraftservermanager.util.kubernetes.metadata
import com.rohengiralt.minecraftservermanager.util.kubernetes.ports
import com.rohengiralt.minecraftservermanager.util.kubernetes.service
import com.rohengiralt.minecraftservermanager.util.kubernetes.spec
import io.kubernetes.client.custom.IntOrString

/**
 * A configuration of a minecraft service that will expose a monitor
 * @param serviceName the minecraft service to configure
 * @param monitorID a unique ID identifying the monitor
 */
fun monitorMinecraftService(
    serviceName: String,
    monitorID: String,
    minecraftPort: Int,
) = service {
    metadata {
        name = serviceName
    }

    spec {
        type = "ClusterIP"
        selector = monitorLabel(monitorID)
        ports {
            port {
                name = "minecraft"
                protocol = "TCP"
                port = minecraftPort
                targetPort = IntOrString(monitorMinecraftContainerPortName())
            }
        }
    }
}