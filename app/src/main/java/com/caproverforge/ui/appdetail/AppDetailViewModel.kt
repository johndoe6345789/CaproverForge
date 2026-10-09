package com.caproverforge.ui.appdetail

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.caproverforge.data.App
import com.caproverforge.data.AppDefinition
import com.caproverforge.data.AppVersion
import com.caproverforge.data.BuildLogs
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.DockerLogParser
import com.caproverforge.data.NodeInfo
import com.caproverforge.data.Project
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.common.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

data class AppDetailData(
    val app: App,
    val rootDomain: String,
    val captainSubDomain: String,
    val defaultNginxConfig: String,
    val projects: List<Project>,
    val nodes: List<NodeInfo>,
) {
    val def: AppDefinition get() = app.def
}

class AppDetailViewModel(
    private val repo: CapRoverRepository,
    val appName: String,
) : LoadingViewModel<AppDetailData>() {

    /** Editable copy of the definition; differs from the server copy while there are unsaved changes. */
    var draft by mutableStateOf<AppDefinition?>(null)
        private set

    val hasChanges: Boolean get() = draft != null && data != null && draft != data?.def

    var buildLogs by mutableStateOf<BuildLogs?>(null)
        private set
    var appLogs by mutableStateOf<String?>(null)
        private set
    var appLogsError by mutableStateOf<String?>(null)
        private set

    init { refresh() }

    override suspend fun load(): AppDetailData = coroutineScope {
        val app = async { repo.app(appName) }
        val projects = async { runCatching { repo.projects() }.getOrDefault(emptyList()) }
        val nodes = async { runCatching { repo.nodes() }.getOrDefault(emptyList()) }
        val (found, all) = app.await()
        val result = AppDetailData(found, all.rootDomain, all.captainSubDomain, all.defaultNginxConfig, projects.await(), nodes.await())
        // Keep unsaved edits across refreshes; otherwise follow the server.
        if (draft == null || draft == data?.def) draft = result.def
        result
    }

    fun edit(transform: (AppDefinition) -> AppDefinition) {
        val current = draft ?: data?.def ?: return
        draft = transform(current)
    }

    fun discardChanges() {
        draft = data?.def
    }

    /** Problems that would make CapRover reject (or silently mangle) the draft. */
    fun validationError(): String? {
        val d = draft ?: return null
        val auth = d.httpAuth
        val repo = d.appPushWebhook?.repoInfo
        return when {
            repo != null && (repo.repo.isBlank() || repo.branch.isBlank()) -> "The Git repository needs a URL and a branch."
            repo != null && repo.sshKey.isNullOrBlank() && (repo.user.isBlank() || repo.password.isBlank()) ->
                "The Git repository needs a username and password/token, or an SSH key."
            d.envVars.any { it.key.isBlank() } -> "Every environment variable needs a name."
            d.envVars.groupBy { it.key }.any { it.value.size > 1 } -> "Environment variable names must be unique."
            d.ports.any { it.hostPort !in 1..65535 || it.containerPort !in 1..65535 } -> "Port mappings need ports between 1 and 65535."
            d.volumes.any { it.containerPath.isBlank() } -> "Every persistent directory needs a path in the container."
            d.volumes.any { v -> v.volumeName.isNullOrBlank() && v.hostPath.isNullOrBlank() } -> "Give each persistent directory a volume name or a server path."
            auth != null && auth.user.isBlank() -> "Basic auth needs a username."
            auth != null && auth.password.isNullOrBlank() && auth.passwordHashed.isNullOrBlank() -> "Basic auth needs a password."
            d.containerHttpPort != null && d.containerHttpPort !in 1..65535 -> "Container HTTP port must be between 1 and 65535."
            else -> null
        }
    }

    fun save() {
        val current = data ?: return
        val updated = draft ?: return
        validationError()?.let { message(it); return }
        action(success = "Saved. ${appName} is restarting with the new settings.") {
            repo.saveApp(current.app, updated)
            draft = null
        }
    }

    fun restart() {
        val current = data ?: return
        action(success = "Restarting $appName…") { repo.restartApp(current.app) }
    }

    fun rename(newName: String, onRenamed: (String) -> Unit) =
        action(success = "Renamed to $newName", reload = false, onSuccess = { onRenamed(newName) }) {
            repo.renameApp(appName, newName)
        }

    fun delete(volumes: List<String>, onDeleted: () -> Unit) {
        action(reload = false, onSuccess = onDeleted) {
            val failed = repo.deleteApps(listOf(appName), volumes)
            message(
                if (failed.isEmpty()) "Deleted $appName"
                else "Deleted $appName. Volumes still in use were kept: ${failed.joinToString()}"
            )
        }
    }

    // region Domains
    fun enableBaseSsl() = action(success = "HTTPS enabled for the default domain") { repo.enableBaseDomainSsl(appName) }
    fun addDomain(domain: String) = action(success = "Connected $domain") { repo.addCustomDomain(appName, domain) }
    fun enableDomainSsl(domain: String) = action(success = "HTTPS enabled for $domain") { repo.enableCustomDomainSsl(appName, domain) }
    fun removeDomain(domain: String) = action(success = "Removed $domain") { repo.removeCustomDomain(appName, domain) }
    // endregion

    // region Deploy
    fun deployImage(image: String) = deployAction("Deploying $image…") { repo.deployImage(appName, image) }
    fun deployDockerfile(dockerfile: String) = deployAction("Build started") { repo.deployDockerfile(appName, dockerfile) }
    fun deployCaptainDefinition(json: String) = deployAction("Build started") { repo.deployCaptainDefinition(appName, json) }
    fun rollback(version: AppVersion) = deployAction("Rolling back to version ${version.version}…") { repo.rollback(appName, version) }

    fun triggerBuild() {
        val token = data?.def?.appPushWebhook?.pushWebhookToken
        if (token.isNullOrBlank()) {
            message("Save the repository settings first.")
            return
        }
        deployAction("Build triggered from the repository") { repo.triggerBuild(token) }
    }

    fun uploadTarball(resolver: ContentResolver, uri: Uri) {
        deployAction("Uploaded. Build started") {
            val name = withContext(Dispatchers.IO) {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            } ?: "source.tar"
            val body = object : RequestBody() {
                override fun contentType() = "application/x-tar".toMediaType()
                override fun writeTo(sink: BufferedSink) {
                    resolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
                        ?: error("Couldn't read the selected file")
                }
            }
            repo.uploadTarball(appName, name, body)
        }
    }

    private fun deployAction(success: String, block: suspend () -> Unit) {
        action(success = success) {
            block()
            buildLogs = buildLogs?.copy(isAppBuilding = true)
        }
    }
    // endregion

    // region Logs
    fun fetchBuildLogs() {
        viewModelScope.launch {
            runCatching { repo.buildLogs(appName) }.onSuccess { logs ->
                val wasBuilding = buildLogs?.isAppBuilding == true
                buildLogs = logs
                // A build just finished: pick up the new version list / deployed version.
                if (wasBuilding && !logs.isAppBuilding) refresh()
            }
        }
    }

    fun fetchAppLogs() {
        viewModelScope.launch {
            runCatching { repo.appLogs(appName) }
                .onSuccess { appLogs = it; appLogsError = null }
                .onFailure { appLogsError = it.userMessage() }
        }
    }

    fun buildLogLines(): List<String> =
        buildLogs?.logs?.lines.orEmpty().flatMap { DockerLogParser.stripAnsi(it).split('\n') }.dropLastWhile { it.isBlank() }
    // endregion
}
