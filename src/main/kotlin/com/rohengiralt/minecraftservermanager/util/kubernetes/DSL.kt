package com.rohengiralt.minecraftservermanager.util.kubernetes

import io.kubernetes.client.openapi.models.*

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class KubernetesDSLMarker

fun deployment(block: (@KubernetesDSLMarker V1Deployment).() -> Unit): V1Deployment {
    return V1Deployment().apply(block)
}

fun V1Deployment.metadata(block: (@KubernetesDSLMarker V1ObjectMeta).() -> Unit) {
    metadata = V1ObjectMeta().apply(block)
}

fun V1Deployment.spec(block: (@KubernetesDSLMarker V1DeploymentSpec).() -> Unit) {
    spec = V1DeploymentSpec().apply(block)
}

fun V1DeploymentSpec.selector(block: (@KubernetesDSLMarker V1LabelSelector).() -> Unit) {
    selector = V1LabelSelector().apply(block)
}

fun V1DeploymentSpec.template(block: (@KubernetesDSLMarker V1PodTemplateSpec).() -> Unit) {
    template = V1PodTemplateSpec().apply(block)
}

fun V1PodTemplateSpec.metadata(block: (@KubernetesDSLMarker V1ObjectMeta).() -> Unit) {
    metadata = V1ObjectMeta().apply(block)
}

fun V1PodTemplateSpec.spec(block: (@KubernetesDSLMarker V1PodSpec).() -> Unit) {
    spec = V1PodSpec().apply(block)
}

class V1ContainersDSL(private val containers: MutableList<V1Container>) {
    fun container(block: (@KubernetesDSLMarker V1Container).() -> Unit) {
        containers.add(V1Container().apply(block))
    }
}

fun V1PodSpec.containers(block: (@KubernetesDSLMarker V1ContainersDSL).() -> Unit) {
    val containers = mutableListOf<V1Container>()
    V1ContainersDSL(containers).apply(block)
    this.containers = containers
}

class V1VolumeMountsDSL(private val volumeMounts: MutableList<V1VolumeMount>) {
    fun volumeMount(block: (@KubernetesDSLMarker V1VolumeMount).() -> Unit) {
        volumeMounts.add(V1VolumeMount().apply(block))
    }
}

fun V1Container.volumeMounts(block: (@KubernetesDSLMarker V1VolumeMountsDSL).() -> Unit) {
    val volumeMounts = mutableListOf<V1VolumeMount>()
    V1VolumeMountsDSL(volumeMounts).apply(block)
    this.volumeMounts = volumeMounts
}

class V1EnvDSL(private val envVars: MutableList<V1EnvVar>) {
    fun `var`(block: V1EnvVar.() -> Unit) {
        envVars.add(V1EnvVar().apply(block))
    }
}

fun V1Container.env(block: (@KubernetesDSLMarker V1EnvDSL).() -> Unit) {
    val envVars = mutableListOf<V1EnvVar>()
    V1EnvDSL(envVars).apply(block)
    this.env = envVars
}

fun V1EnvVar.valueFrom(block: (@KubernetesDSLMarker V1EnvVarSource).() -> Unit) {
    valueFrom = V1EnvVarSource().apply(block)
}

fun V1EnvVarSource.secretKeyRef(block: (@KubernetesDSLMarker V1SecretKeySelector).() -> Unit) {
    secretKeyRef = V1SecretKeySelector().apply(block)
}

class V1PortsDSL(private val ports: MutableList<V1ContainerPort>) {
    fun port(block: (@KubernetesDSLMarker V1ContainerPort).() -> Unit) {
        ports.add(V1ContainerPort().apply(block))
    }
}

fun V1Container.ports(block: (@KubernetesDSLMarker V1PortsDSL).() -> Unit) {
    val ports = mutableListOf<V1ContainerPort>()
    V1PortsDSL(ports).apply(block)
    this.ports = ports
}

class V1VolumesDSL(private val volumes: MutableList<V1Volume>) {
    fun volume(block: (@KubernetesDSLMarker V1Volume).() -> Unit) {
        volumes.add(V1Volume().apply(block))
    }
}

fun V1PodSpec.volumes(block: (@KubernetesDSLMarker V1VolumesDSL).() -> Unit) {
    val volumes = mutableListOf<V1Volume>()
    V1VolumesDSL(volumes).apply(block)
    this.volumes = volumes
}

fun V1Volume.persistentVolumeClaim(block: (@KubernetesDSLMarker V1PersistentVolumeClaimVolumeSource).() -> Unit) {
    persistentVolumeClaim = V1PersistentVolumeClaimVolumeSource().apply(block)
}

fun service(block: (@KubernetesDSLMarker V1Service).() -> Unit): V1Service =
    V1Service().apply(block)

fun V1Service.metadata(block: (@KubernetesDSLMarker V1ObjectMeta).() -> Unit) {
    metadata = V1ObjectMeta().apply(block)
}

fun V1Service.spec(block: (@KubernetesDSLMarker V1ServiceSpec).() -> Unit) {
    spec = V1ServiceSpec().apply(block)
}

class V1ServicePortsDSL(private val servicePorts: MutableList<V1ServicePort>) {
    fun port(block: (@KubernetesDSLMarker V1ServicePort).() -> Unit) {
        servicePorts.add(V1ServicePort().apply(block))
    }
}

fun V1ServiceSpec.ports(block: (@KubernetesDSLMarker V1ServicePortsDSL).() -> Unit) {
    val ports = mutableListOf<V1ServicePort>()
    V1ServicePortsDSL(ports).apply(block)
    this.ports = ports
}

fun persistentVolumeClaim(block: (@KubernetesDSLMarker V1PersistentVolumeClaim).() -> Unit): V1PersistentVolumeClaim =
    V1PersistentVolumeClaim().apply(block)

fun V1PersistentVolumeClaim.metadata(block: (@KubernetesDSLMarker V1ObjectMeta).() -> Unit) {
    metadata = V1ObjectMeta().apply(block)
}

fun V1PersistentVolumeClaim.spec(block: (@KubernetesDSLMarker V1PersistentVolumeClaimSpec).() -> Unit) {
    spec = V1PersistentVolumeClaimSpec().apply(block)
}

fun V1PersistentVolumeClaimSpec.resources(block: (@KubernetesDSLMarker V1VolumeResourceRequirements).() -> Unit) {
    resources = V1VolumeResourceRequirements().apply(block)
}

fun secret(block: (@KubernetesDSLMarker V1Secret).() -> Unit): V1Secret =
    V1Secret().apply(block)

fun V1Secret.metadata(block: (@KubernetesDSLMarker V1ObjectMeta).() -> Unit) {
    metadata = V1ObjectMeta().apply(block)
}