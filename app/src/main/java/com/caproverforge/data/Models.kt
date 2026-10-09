package com.caproverforge.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Mirrors the TypeScript models of the official `caprover-api` client (v2 REST API).

@Serializable
data class CaptainInfo(
    val hasRootSsl: Boolean = false,
    val forceSsl: Boolean = false,
    val rootDomain: String = "",
    val captainSubDomain: String = "captain",
)

@Serializable
data class VersionInfo(
    val currentVersion: String = "",
    val latestVersion: String = "",
    val canUpdate: Boolean = false,
    val changeLogMessage: String = "",
)

@Serializable
data class LoadBalancerInfo(
    val activeConnections: Long = 0,
    val accepted: Long = 0,
    val handled: Long = 0,
    val total: Long = 0,
    val reading: Long = 0,
    val writing: Long = 0,
    val waiting: Long = 0,
)

@Serializable
data class NodeInfo(
    val nodeId: String = "",
    val type: String = "",
    val isLeader: Boolean = false,
    val hostname: String = "",
    val architecture: String = "",
    val operatingSystem: String = "",
    val nanoCpu: Long = 0,
    val memoryBytes: Long = 0,
    val dockerEngineVersion: String = "",
    val ip: String = "",
    val state: String = "",
    val status: String = "",
)

@Serializable
data class Project(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val parentProjectId: String? = null,
)

@Serializable
data class EnvVar(val key: String = "", val value: String = "")

@Serializable
data class PortMapping(
    val containerPort: Int = 0,
    val hostPort: Int = 0,
    val protocol: String? = null,
    val publishMode: String? = null,
)

@Serializable
data class Volume(
    val containerPath: String = "",
    val volumeName: String? = null,
    val hostPath: String? = null,
    val mode: String? = null,
) {
    val isBind: Boolean get() = !hostPath.isNullOrBlank()
}

@Serializable
data class AppVersion(
    val version: Int = 0,
    val deployedImageName: String? = null,
    val timeStamp: String = "",
    val gitHash: String? = null,
)

@Serializable
data class CustomDomain(val publicDomain: String = "", val hasSsl: Boolean = false)

@Serializable
data class AppTag(val tagName: String = "")

@Serializable
data class RepoInfo(
    val repo: String = "",
    val branch: String = "",
    val user: String = "",
    val password: String = "",
    val sshKey: String? = null,
)

@Serializable
data class PushWebhook(
    val tokenVersion: String? = null,
    val repoInfo: RepoInfo? = null,
    val pushWebhookToken: String? = null,
)

@Serializable
data class HttpAuth(
    val user: String = "",
    val password: String? = null,
    val passwordHashed: String? = null,
)

@Serializable
data class DeployTokenConfig(val enabled: Boolean = false, val appDeployToken: String? = null)

@Serializable
data class AppDefinition(
    val appName: String = "",
    val projectId: String? = null,
    val description: String? = null,
    val deployedVersion: Int = 0,
    val notExposeAsWebApp: Boolean = false,
    val hasPersistentData: Boolean = false,
    val hasDefaultSubDomainSsl: Boolean = false,
    val containerHttpPort: Int? = null,
    val captainDefinitionRelativeFilePath: String = "./captain-definition",
    val forceSsl: Boolean = false,
    val websocketSupport: Boolean = false,
    val nodeId: String? = null,
    val instanceCount: Int = 1,
    val preDeployFunction: String? = null,
    val serviceUpdateOverride: String? = null,
    val customNginxConfig: String? = null,
    val redirectDomain: String? = null,
    val customDomain: List<CustomDomain> = emptyList(),
    val tags: List<AppTag> = emptyList(),
    val ports: List<PortMapping> = emptyList(),
    val volumes: List<Volume> = emptyList(),
    val envVars: List<EnvVar> = emptyList(),
    val versions: List<AppVersion> = emptyList(),
    val appDeployTokenConfig: DeployTokenConfig? = null,
    val appPushWebhook: PushWebhook? = null,
    val httpAuth: HttpAuth? = null,
    val isAppBuilding: Boolean = false,
) {
    val deployedImage: String?
        get() = versions.firstOrNull { it.version == deployedVersion }?.deployedImageName

    /** True while the app still runs CapRover's placeholder image (nothing has been deployed yet). */
    val isPlaceholder: Boolean
        get() = deployedImage?.contains("caprover-placeholder-app") == true || versions.isEmpty()
}

/** An app definition plus the untouched JSON it came from, so saves never drop unknown fields. */
data class App(val def: AppDefinition, val raw: JsonObject)

data class AppsData(
    val apps: List<App>,
    val rootDomain: String,
    val captainSubDomain: String,
    val defaultNginxConfig: String,
)

@Serializable
data class BuildLogs(
    val isAppBuilding: Boolean = false,
    val isBuildFailed: Boolean = false,
    val logs: BuildLogLines = BuildLogLines(),
)

@Serializable
data class BuildLogLines(val lines: List<String> = emptyList(), val firstLineNumber: Int = 0)

@Serializable
data class UnusedImage(val id: String = "", val tags: List<String> = emptyList())

@Serializable
data class DiskCleanupConfig(
    val mostRecentLimit: Int = 0,
    val cronSchedule: String = "",
    val timezone: String = "",
)

@Serializable
data class Registry(
    val id: String = "",
    val registryUser: String = "",
    val registryPassword: String = "",
    val registryDomain: String = "",
    val registryImagePrefix: String = "",
    val registryType: String = "REMOTE_REG",
) {
    val isSelfHosted: Boolean get() = registryType == "LOCAL_REG"
}

data class Registries(val registries: List<Registry>, val defaultPushRegistryId: String?)

@Serializable
data class NginxConfigPart(val byDefault: String = "", val customValue: String? = null)

@Serializable
data class NginxConfig(
    val baseConfig: NginxConfigPart = NginxConfigPart(),
    val captainConfig: NginxConfigPart = NginxConfigPart(),
)

@Serializable
data class OneClickAppSummary(
    val name: String = "",
    val displayName: String = "",
    val description: String = "",
    val logoUrl: String = "",
    val baseUrl: String = "",
    val isOfficial: Boolean = false,
)

@Serializable
data class OneClickVariable(
    val id: String = "",
    val label: String = "",
    val defaultValue: String? = null,
    val validRegex: String? = null,
    val description: String? = null,
)

@Serializable
data class OneClickDeployState(
    val steps: List<String> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val currentStep: Int = 0,
)
