package com.caproverforge.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.RequestBody

/** Typed access to the CapRover API; one method per dashboard operation. */
class CapRoverRepository(
    val api: CapRoverApi,
    private val sessionStore: SessionStore,
) {
    private inline fun <reified T> JsonElement.decode(): T = ApiJson.decodeFromJsonElement(this)
    private fun JsonElement.field(name: String): JsonElement = (this as? JsonObject)?.get(name) ?: JsonNull

    // region Session
    suspend fun login(serverInput: String, password: String, otp: String?) {
        val baseUrl = ServerAddress.normalize(serverInput)
        val token = api.login(baseUrl, password, otp)
        sessionStore.save(baseUrl, token)
    }

    fun logout() = sessionStore.clear()
    // endregion

    // region System
    suspend fun captainInfo(): CaptainInfo = api.get("/user/system/info").decode()
    suspend fun versionInfo(): VersionInfo = api.get("/user/system/versioninfo").decode()
    suspend fun performUpdate(latestVersion: String) {
        api.post("/user/system/versioninfo", buildJsonObject { put("latestVersion", latestVersion) })
    }

    suspend fun loadBalancerInfo(): LoadBalancerInfo = api.get("/user/system/loadbalancerinfo").decode()
    suspend fun nodes(): List<NodeInfo> = api.get("/user/system/nodes").field("nodes").decode()

    suspend fun addNode(
        nodeType: String,
        privateKey: String,
        remoteIp: String,
        sshPort: String,
        sshUser: String,
        captainIp: String,
    ) {
        api.post("/user/system/nodes", buildJsonObject {
            put("nodeType", nodeType)
            put("privateKey", privateKey)
            put("remoteNodeIpAddress", remoteIp)
            put("sshPort", sshPort)
            put("sshUser", sshUser)
            put("captainIpAddress", captainIp)
        })
    }

    suspend fun changeRootDomain(rootDomain: String, force: Boolean) {
        api.post("/user/system/changerootdomain", buildJsonObject {
            put("rootDomain", rootDomain)
            put("force", force)
        })
    }

    suspend fun enableRootSsl(email: String) {
        api.post("/user/system/enablessl", buildJsonObject { put("emailAddress", email) })
    }

    suspend fun setForceSsl(enabled: Boolean) {
        api.post("/user/system/forcessl", buildJsonObject { put("isEnabled", enabled) })
    }

    suspend fun changePassword(oldPassword: String, newPassword: String) {
        api.post("/user/changepassword", buildJsonObject {
            put("oldPassword", oldPassword)
            put("newPassword", newPassword)
        })
    }

    suspend fun nginxConfig(): NginxConfig = api.get("/user/system/nginxconfig").decode()
    suspend fun setNginxConfig(base: String, captain: String) {
        api.post("/user/system/nginxconfig", buildJsonObject {
            put("baseConfig", buildJsonObject { put("customValue", base) })
            put("captainConfig", buildJsonObject { put("customValue", captain) })
        })
    }

    /** NetData settings are passed through untouched apart from the fields we edit. */
    suspend fun netData(): JsonObject = api.get("/user/system/netdata").jsonObject
    suspend fun setNetData(info: JsonObject) {
        api.post("/user/system/netdata", buildJsonObject { put("netDataInfo", info) })
    }

    suspend fun createBackupUrl(): String {
        val token = api.post("/user/system/createbackup", buildJsonObject {
            put("postDownloadFileName", "backup.tar")
        }).field("downloadToken").let { it as? JsonPrimitive }?.contentOrNull
            ?: throw CapRoverException(CapRoverException.HTTP, "CapRover didn't return a backup download link.")
        return api.absoluteUrl("/downloads/") + "?namespace=${CapRoverApi.NAMESPACE}&downloadToken=" +
            java.net.URLEncoder.encode(token, "UTF-8")
    }

    suspend fun unusedImages(mostRecentLimit: Int): List<UnusedImage> =
        api.get("/user/apps/appDefinitions/unusedImages", mapOf("mostRecentLimit" to "$mostRecentLimit"))
            .field("unusedImages").decode()

    suspend fun deleteImages(ids: List<String>) {
        api.post("/user/apps/appDefinitions/deleteImages", buildJsonObject {
            putJsonArray("imageIds") { ids.forEach { add(JsonPrimitive(it)) } }
        })
    }

    suspend fun diskCleanup(): DiskCleanupConfig = api.get("/user/system/diskcleanup").decode()
    suspend fun setDiskCleanup(config: DiskCleanupConfig) {
        api.post("/user/system/diskcleanup", ApiJson.encodeToJsonElement(config))
    }
    // endregion

    // region Registries
    suspend fun registries(): Registries {
        val data = api.get("/user/registries")
        return Registries(
            registries = data.field("registries").decode(),
            defaultPushRegistryId = (data.field("defaultPushRegistryId") as? JsonPrimitive)?.contentOrNull,
        )
    }

    suspend fun addRegistry(registry: Registry) {
        api.post("/user/registries/insert", ApiJson.encodeToJsonElement(registry))
    }

    suspend fun updateRegistry(registry: Registry) {
        api.post("/user/registries/update", ApiJson.encodeToJsonElement(registry))
    }

    suspend fun deleteRegistry(id: String) {
        api.post("/user/registries/delete", buildJsonObject { put("registryId", id) })
    }

    suspend fun setDefaultPushRegistry(id: String) {
        api.post("/user/registries/setpush", buildJsonObject { put("registryId", id) })
    }

    suspend fun enableSelfHostedRegistry() {
        api.post("/user/system/selfhostregistry/enableregistry")
    }

    suspend fun disableSelfHostedRegistry() {
        api.post("/user/system/selfhostregistry/disableregistry")
    }
    // endregion

    // region Projects
    suspend fun projects(): List<Project> = runCatching {
        api.get("/user/projects").field("projects").decode<List<Project>>()
    }.getOrElse { e ->
        // Projects were added in CapRover 1.13; older servers simply don't have them.
        if (e is CapRoverException && e.status == CapRoverException.HTTP) emptyList() else throw e
    }

    suspend fun createProject(name: String, description: String, parentId: String?) {
        api.post("/user/projects/register", buildJsonObject {
            put("id", "")
            put("name", name)
            put("description", description)
            if (!parentId.isNullOrBlank()) put("parentProjectId", parentId)
        })
    }

    suspend fun updateProject(project: Project) {
        api.post("/user/projects/update", buildJsonObject {
            put("projectDefinition", ApiJson.encodeToJsonElement(project))
        })
    }

    suspend fun deleteProjects(ids: List<String>) {
        api.post("/user/projects/delete", buildJsonObject {
            putJsonArray("projectIds") { ids.forEach { add(JsonPrimitive(it)) } }
        })
    }
    // endregion

    // region Apps
    suspend fun apps(): AppsData {
        val data = api.get("/user/apps/appDefinitions").jsonObject
        val apps = data["appDefinitions"]?.jsonArray.orEmpty().map { element ->
            val raw = element.jsonObject
            App(ApiJson.decodeFromJsonElement<AppDefinition>(raw), raw)
        }.sortedBy { it.def.appName }
        return AppsData(
            apps = apps,
            rootDomain = data["rootDomain"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            captainSubDomain = data["captainSubDomain"]?.jsonPrimitive?.contentOrNull ?: "captain",
            defaultNginxConfig = data["defaultNginxConfig"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    suspend fun app(appName: String): Pair<App, AppsData> {
        val all = apps()
        val app = all.apps.firstOrNull { it.def.appName == appName }
            ?: throw CapRoverException(CapRoverException.HTTP, "App “$appName” no longer exists.")
        return app to all
    }

    suspend fun createApp(appName: String, projectId: String?, hasPersistentData: Boolean) {
        api.post("/user/apps/appDefinitions/register?detached=1", buildJsonObject {
            put("appName", appName)
            put("projectId", projectId.orEmpty())
            put("hasPersistentData", hasPersistentData)
        })
    }

    /**
     * Saves an app the same way the web dashboard's “Save & Restart” does: the full definition
     * is posted back. Fields this app doesn't model are carried over from [App.raw] untouched.
     * CapRover forces a service update on every save, so this also restarts the app.
     */
    suspend fun saveApp(app: App, updated: AppDefinition) {
        val encoded = updatedJson(updated)
        val merged = JsonObject(
            (app.raw + encoded + ("appName" to JsonPrimitive(app.def.appName))) - READ_ONLY_APP_FIELDS
        )
        api.post("/user/apps/appDefinitions/update", merged)
    }

    private fun updatedJson(def: AppDefinition): JsonObject {
        val obj = ApiJson.encodeToJsonElement(def).jsonObject.toMutableMap()
        // Explicitly clear optional settings the user removed (the encoder drops nulls).
        if (def.httpAuth == null) obj["httpAuth"] = JsonNull
        // An empty port field means "default" (CapRover falls back to 80).
        if (def.containerHttpPort == null) obj["containerHttpPort"] = JsonNull
        // No repo configured means "remove the git webhook" (CapRover drops it on an empty repoInfo).
        if (def.appPushWebhook?.repoInfo == null) obj["appPushWebhook"] = JsonNull
        return JsonObject(obj)
    }

    suspend fun restartApp(app: App) = saveApp(app, app.def)

    private companion object {
        /** Server-computed fields that the update endpoint ignores; no point sending them back. */
        val READ_ONLY_APP_FIELDS = setOf("versions", "isAppBuilding", "deployedVersion")
    }

    suspend fun renameApp(oldName: String, newName: String) {
        api.post("/user/apps/appDefinitions/rename", buildJsonObject {
            put("oldAppName", oldName)
            put("newAppName", newName)
        })
    }

    /** Returns the volumes the server refused to delete (still in use elsewhere). */
    suspend fun deleteApps(appNames: List<String>, volumes: List<String>): List<String> {
        val data = api.post("/user/apps/appDefinitions/delete", buildJsonObject {
            if (appNames.size == 1) put("appName", appNames.first())
            else putJsonArray("appNames") { appNames.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("volumes") { volumes.forEach { add(JsonPrimitive(it)) } }
        })
        return (data.field("volumesFailedToDelete") as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    }

    suspend fun enableBaseDomainSsl(appName: String) {
        api.post("/user/apps/appDefinitions/enablebasedomainssl", buildJsonObject { put("appName", appName) })
    }

    suspend fun addCustomDomain(appName: String, domain: String) {
        api.post("/user/apps/appDefinitions/customdomain", buildJsonObject {
            put("appName", appName)
            put("customDomain", domain)
        })
    }

    suspend fun enableCustomDomainSsl(appName: String, domain: String) {
        api.post("/user/apps/appDefinitions/enablecustomdomainssl", buildJsonObject {
            put("appName", appName)
            put("customDomain", domain)
        })
    }

    suspend fun removeCustomDomain(appName: String, domain: String) {
        api.post("/user/apps/appDefinitions/removecustomdomain", buildJsonObject {
            put("appName", appName)
            put("customDomain", domain)
        })
    }

    suspend fun buildLogs(appName: String): BuildLogs = api.get("/user/apps/appData/$appName").decode()

    suspend fun appLogs(appName: String): String {
        val hex = api.get("/user/apps/appData/$appName/logs", mapOf("encoding" to "hex"))
            .field("logs").jsonPrimitive.contentOrNull.orEmpty()
        return DockerLogParser.parseHex(hex)
    }

    private suspend fun deployDefinition(appName: String, definition: JsonObject, gitHash: String = "") {
        api.post("/user/apps/appData/$appName?detached=1", buildJsonObject {
            put("captainDefinitionContent", definition.toString())
            put("gitHash", gitHash)
        })
    }

    suspend fun deployImage(appName: String, imageName: String) = deployDefinition(appName, buildJsonObject {
        put("schemaVersion", 2)
        put("imageName", imageName.trim())
    })

    suspend fun deployDockerfile(appName: String, dockerfile: String) = deployDefinition(appName, buildJsonObject {
        put("schemaVersion", 2)
        put("dockerfileLines", buildJsonArray { dockerfile.lines().forEach { add(JsonPrimitive(it)) } })
    })

    suspend fun deployCaptainDefinition(appName: String, captainDefinitionJson: String) {
        val parsed = runCatching { ApiJson.parseToJsonElement(captainDefinitionJson).jsonObject }.getOrElse {
            throw CapRoverException(CapRoverException.HTTP, "captain-definition is not valid JSON.")
        }
        deployDefinition(appName, parsed)
    }

    /** Same approach as the dashboard: re-deploy a previous image via `FROM <image>`. */
    suspend fun rollback(appName: String, version: AppVersion) {
        val image = version.deployedImageName
            ?: throw CapRoverException(CapRoverException.HTTP, "Version ${version.version} has no image to roll back to.")
        deployDefinition(appName, buildJsonObject {
            put("schemaVersion", 2)
            put("dockerfileLines", buildJsonArray { add(JsonPrimitive("FROM $image")) })
        }, version.gitHash.orEmpty())
    }

    suspend fun uploadTarball(appName: String, fileName: String, body: RequestBody) {
        api.postMultipart("/user/apps/appData/$appName?detached=1", api.buildPart("sourceFile", fileName, body))
    }

    suspend fun triggerBuild(pushWebhookToken: String) {
        api.post("/user/apps/webhooks/triggerbuild?namespace=${CapRoverApi.NAMESPACE}&token=" +
            java.net.URLEncoder.encode(pushWebhookToken, "UTF-8"))
    }
    // endregion

    // region One-click apps
    suspend fun oneClickApps(): List<OneClickAppSummary> =
        api.get("/user/oneclick/template/list").field("oneClickApps").decode()

    suspend fun oneClickTemplate(name: String, baseUrl: String): JsonObject =
        api.get("/user/oneclick/template/app", mapOf("appName" to name, "baseDomain" to baseUrl))
            .field("appTemplate").jsonObject

    suspend fun oneClickRepositories(): List<String> =
        api.get("/user/oneclick/repositories").field("urls").decode()

    suspend fun addOneClickRepository(url: String) {
        api.post("/user/oneclick/repositories/insert", buildJsonObject { put("repositoryUrl", url) })
    }

    suspend fun deleteOneClickRepository(url: String) {
        api.post("/user/oneclick/repositories/delete", buildJsonObject { put("repositoryUrl", url) })
    }

    suspend fun startOneClickDeploy(template: JsonObject, values: Map<String, String>, templateName: String): String =
        api.post("/user/oneclick/deploy", buildJsonObject {
            put("template", template)
            put("values", buildJsonArray {
                values.forEach { (k, v) -> add(buildJsonObject { put("key", k); put("value", v) }) }
            })
            put("templateName", templateName)
        }).field("jobId").let { it as? JsonPrimitive }?.contentOrNull
            ?: throw CapRoverException(CapRoverException.HTTP, "This CapRover version can't run one-click deployments from the app. Update CapRover and try again.")

    suspend fun oneClickProgress(jobId: String): OneClickDeployState =
        api.get("/user/oneclick/deploy/progress", mapOf("jobId" to jobId)).decode()
    // endregion
}
