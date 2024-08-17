package com.rohengiralt.minecraftservermanager.util.kubernetes

import io.kubernetes.client.custom.V1Patch
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.apis.AppsV1Api
import io.kubernetes.client.openapi.models.V1Deployment
import io.kubernetes.client.util.PatchUtils
import kotlinx.datetime.Clock
import okhttp3.Call
import java.security.SecureRandom
import java.util.*

/**
 * Restarts a deployment
 * @param name the name of the deployment
 * @param namespace the namespace of the deployment
 */
fun AppsV1Api.restartDeployment(name: String, namespace: String): Boolean {
    try {
        PatchUtils.patch(
            V1Deployment::class.java,
            { restartPatchCall(name, namespace) },
            V1Patch.PATCH_FORMAT_STRATEGIC_MERGE_PATCH,
            apiClient
        )

        logger.trace("Successfully restarted deployment {}", name)
        return true
    } catch (e: ApiException) {
        logger.error("Failed to restart deployment {}", name, e)
        return false
    }
}

/**
 * Returns a Kubernetes API call to restart a deployment.
 */
private fun AppsV1Api.restartPatchCall(
    name: String,
    namespace: String
): Call? = patchNamespacedDeployment(name, namespace, V1Patch(newRestartPatchBody())).buildCall(null)

/**
 * Returns the body of a Kubernetes API call to restart a deployment.
 */
private fun newRestartPatchBody(): String {
    // Implementation note:
    // There is no official API call to restart a deployment in Kubernetes.
    // Instead, we take the route of kubectl (https://stackoverflow.com/a/59051313/13160488)
    // and just change an annotation, causing k8s to restart the deployment.
    // In particular, we add annotations that include the current time
    // along with a random number to ensure that each update is distinct.


    val currentTime = Clock.System.now()
    val restartId = random.nextLong()

    return """
    {
        "spec": {
            "template": {
                "metadata": {
                    "annotations": {
                        "msm/restartedAt": "${currentTime.toEpochMilliseconds()}",
                        "msm/restartId": "$restartId"
                    }
                }
            }
        }
    }
    """.trimIndent()
}

private val random: Random = SecureRandom() // SecureRandom to prevent multiple runs of the app generating the same number