package com.rohengiralt.minecraftservermanager.util.kubernetes

import com.rohengiralt.minecraftservermanager.util.guava.typeTokenOf
import com.rohengiralt.minecraftservermanager.util.kubernetes.KnownWatchType.*
import io.kubernetes.client.common.KubernetesObject
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.util.Watch
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.slf4j.LoggerFactory

/**
 * Creates a new [Watch]
 * @param client the client to watch with
 * @param request a list request to the resource to watch
 * @param resourceVersion the initial resource version to start watching from
 */
inline fun <reified T : KubernetesObject> createWatch(client: ApiClient, request: KubernetesRequest<T, *, *>, resourceVersion: String): Watch<T> =
    Watch.createWatch(client, request.buildCall(watch = true, resourceVersion = resourceVersion/*, resourceVersionMatch = "NotOlderThan"*/), typeTokenOf<Watch.Response<T>>().type)

/**
 * Watches the value of a particular list request.
 */
context(CoroutineScope)
inline fun <reified T : KubernetesObject> ApiClient.watch(crossinline createRequest: () -> KubernetesRequest<T, *, *>): StateFlow<Set<T>> {
    val client = this
    val initialResponse = createRequest().execute()
    @Suppress("UNCHECKED_CAST")
    val initialItems = initialResponse.items as List<T>

    val state = MutableStateFlow(initialItems.toSet())

    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val list = createRequest().execute()
                val currentVersion = list.metadata.resourceVersion
                @Suppress("UNCHECKED_CAST")
                state.update { (list.items as List<T>).toSet() }

                val watch = createWatch(client, createRequest(), currentVersion)
                logger.trace("Successfully created watch. Updating state.")
                watch.updateInto(state)
            } catch (e: ApiException) {
                logger.warn("Received error HTTP response code from watch; aborting and recreating connection.", e)
            }
        }
    }

    return state
}

/**
 * Collects the responses from a [Watch] and emits them into [out]
 */
context(CoroutineScope)
@PublishedApi
internal suspend fun <T : KubernetesObject> Watch<T>.updateInto(out: MutableSharedFlow<Set<T>>) {
    val itemsByQualifiedName = mutableMapOf<QualifiedName, T>()

    for (response in this) {
        val success = handleResponse<T>(response, itemsByQualifiedName)
        if (!success) {
            logger.debug("Failed to handle response from watch. Resetting connection.")
            break // Reset connection
        }

        logger.trace("Emitting new items update")
        out.emit(itemsByQualifiedName.values.toSet()) // TODO: might need to improve efficiency here; does an O(n) copy on every update

        ensureActive() // Allow coroutine cancellation
    }
}

/**
 * Updates [itemsByQualifiedName] based on a new response from the watch.
 */
private fun <T : KubernetesObject> handleResponse(
    response: Watch.Response<T>,
    itemsByQualifiedName: MutableMap<QualifiedName, T>
): Boolean {
    val name = QualifiedName(
        name = response.`object`.metadata.namespace,
        namespace = response.`object`.metadata.namespace
    )

    when (response.knownType) {
        ADDED -> {
            assert(itemsByQualifiedName[name] == null)
            itemsByQualifiedName[name] = response.`object`
        }

        MODIFIED -> {
            assert(itemsByQualifiedName[name] != null)
            itemsByQualifiedName[name] = response.`object`
        }

        DELETED -> {
            assert(itemsByQualifiedName[name] != null)
            itemsByQualifiedName.remove(name)
        }

        ERROR -> {
            logger.warn(
                """
                    |Received ERROR response from watch; aborting and recreating connection. Response:
                    |Status: ${response.status}
                    |Object: ${response.`object`}
                """.trimMargin()
            )
            return false
        }

        null -> {
            logger.warn(
                """
                    |Received unknown response type from watch; aborting and recreating connection. Response:
                    |Type: ${response.type}
                    |Status: ${response.status}
                    |Object: ${response.`object`}
                """.trimMargin()
            )
            return false
        }
    }

    return true
}

/**
 * Represents a fully qualified (and guaranteed unique by Kubernetes) name for a Kubernetes resource.
 */
private data class QualifiedName(
    val namespace: String?,
    val name: String?
)

val Watch.Response<*>.knownType: KnownWatchType? get() = KnownWatchType.fromString(type)

/**
 * All of the possible types a watch response can have that we know about.
 */
enum class KnownWatchType(val value: String) {
    ADDED("ADDED"),
    MODIFIED("MODIFIED"),
    DELETED("DELETED"),
    ERROR("ERROR");

    companion object {
        fun fromString(type: String): KnownWatchType? = entries.find { it.value == type }
    }
}

@PublishedApi
internal val logger = LoggerFactory.getLogger("Watch")