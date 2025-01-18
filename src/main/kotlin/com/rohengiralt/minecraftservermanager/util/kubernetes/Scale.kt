package com.rohengiralt.minecraftservermanager.util.kubernetes

import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.AppsV1Api
import org.slf4j.LoggerFactory

/**
 * Sets the scale of a deployment.
 * @param name the name of the deployment to scale
 * @param namespace the namespace of the deployment to scale
 * @param replicas the number of replicas to set the deployment to
 */
fun AppsV1Api.scaleDeployment(name: String, namespace: String, replicas: Int): Boolean {
    try {
        // PATCH seems to be broken on the API client currently, so GET and PUT instead. TODO: revisit this later
        val scale = readNamespacedDeploymentScale(name, namespace).execute()
        replaceNamespacedDeploymentScale(name, namespace, scale.apply {
            spec.replicas = replicas
        }).execute()
        logger.trace("Successfully scaled deployment {} to {}", name, replicas)
        return true
    } catch (e: ApiException) {
        logger.error("Failed to scale deployment {} to {}", name, replicas, e)
        return false
    }
}

private val logger = LoggerFactory.getLogger("Scale")