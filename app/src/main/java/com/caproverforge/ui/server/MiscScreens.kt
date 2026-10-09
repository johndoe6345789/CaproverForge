package com.caproverforge.ui.server

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.Project
import com.caproverforge.data.ThemeMode
import com.caproverforge.data.VersionInfo
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.common.userMessage
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.InfoRow
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.PasswordField
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.components.SwitchRow
import com.caproverforge.ui.components.openUrl
import com.caproverforge.ui.navigation.LocalContainer
import com.caproverforge.ui.navigation.containerViewModel
import com.caproverforge.ui.theme.LocalExtendedColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// region Projects
class ProjectsViewModel(private val repo: CapRoverRepository) : LoadingViewModel<Pair<List<Project>, Map<String, Int>>>() {
    init { refresh() }

    override suspend fun load(): Pair<List<Project>, Map<String, Int>> {
        val projects = repo.projects().sortedBy { it.name.lowercase() }
        val counts = repo.apps().apps.groupingBy { it.def.projectId.orEmpty() }.eachCount()
        return projects to counts
    }

    fun save(project: Project) = action(success = if (project.id.isEmpty()) "Project created" else "Project updated") {
        if (project.id.isEmpty()) repo.createProject(project.name, project.description, project.parentProjectId)
        else repo.updateProject(project)
    }

    fun delete(project: Project) = action(success = "Deleted ${project.name}") { repo.deleteProjects(listOf(project.id)) }
}

@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    val vm = containerViewModel { ProjectsViewModel(it.repository) }
    var editing by remember { mutableStateOf<Project?>(null) }
    var deleting by remember { mutableStateOf<Project?>(null) }

    ServerPage(
        "Projects",
        vm,
        onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { editing = Project() }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New project") })
        },
    ) { (projects, counts) ->
        if (projects.isEmpty()) {
            Text(
                "Projects group related apps, for example an app and its database. Requires CapRover 1.13 or newer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        projects.forEach { project ->
            SectionCard(
                project.name,
                icon = Icons.Outlined.Folder,
                subtitle = "${counts[project.id] ?: 0} apps" +
                    (project.parentProjectId?.let { pid -> projects.firstOrNull { it.id == pid }?.name?.let { " · in $it" } } ?: ""),
                trailing = {
                    IconButton(onClick = { editing = project }) { Icon(Icons.Outlined.Edit, "Edit") }
                    IconButton(onClick = { deleting = project }) { Icon(Icons.Outlined.Delete, "Delete") }
                },
            ) {
                if (project.description.isNotBlank()) Text(project.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(72.dp))
    }

    editing?.let { project ->
        var name by rememberSaveable(project.id) { mutableStateOf(project.name) }
        var description by rememberSaveable(project.id) { mutableStateOf(project.description) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (project.id.isEmpty()) "New project" else "Edit project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.save(project.copy(name = name.trim(), description = description.trim())); editing = null }, enabled = name.isNotBlank()) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { project ->
        ConfirmDialog(
            title = "Delete ${project.name}?",
            text = "Apps in this project are not deleted; they just stop belonging to it.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.delete(project) },
            onDismiss = { deleting = null },
        )
    }
}
// endregion

// region Monitoring
class MonitoringViewModel(private val repo: CapRoverRepository) : LoadingViewModel<JsonObject>() {
    init { refresh() }
    override suspend fun load() = repo.netData()

    fun setEnabled(enabled: Boolean) {
        val current = data ?: return
        action(success = if (enabled) "NetData is starting" else "NetData stopped") {
            repo.setNetData(JsonObject(current + ("isEnabled" to JsonPrimitive(enabled))))
        }
    }
}

@Composable
fun MonitoringScreen(onBack: () -> Unit, onOpenStats: () -> Unit = {}) {
    val vm = containerViewModel { MonitoringViewModel(it.repository) }
    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    ServerPage("Monitoring", vm, onBack) { info ->
        val enabled = info["isEnabled"]?.jsonPrimitive?.booleanOrNull == true
        val url = info["netDataUrl"]?.jsonPrimitive?.contentOrNull
        SectionCard(
            "NetData",
            icon = Icons.Outlined.MonitorHeart,
            subtitle = "Real-time CPU, memory, disk and network charts",
            trailing = { StatusPill(if (enabled) "Running" else "Off", if (enabled) ext.running else MaterialTheme.colorScheme.outline) },
        ) {
            SwitchRow(
                title = "Enable NetData",
                subtitle = "Runs a NetData container on this server",
                checked = enabled,
                enabled = !vm.busy,
                onCheckedChange = vm::setEnabled,
            )
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onOpenStats) { Text("View live stats") }
            }
            if (enabled && !url.isNullOrBlank()) {
                OutlinedButton(onClick = { openUrl(context, if (url.startsWith("http")) url else "https://$url") }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                    Text("  Full NetData dashboard")
                }
                Text(
                    "Opens in your browser. You may need to sign in to the CapRover dashboard there first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
// endregion

// region Password
class PasswordViewModel(private val repo: CapRoverRepository) : ViewModel() {
    var current by mutableStateOf("")
    var new by mutableStateOf("")
    var confirm by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val error: String?
        get() = when {
            new.isNotEmpty() && new.length < 8 -> "Use at least 8 characters."
            confirm.isNotEmpty() && confirm != new -> "Passwords don't match."
            else -> null
        }

    fun submit(onDone: () -> Unit) {
        if (busy || error != null || current.isBlank() || new.isBlank() || new != confirm) return
        viewModelScope.launch {
            busy = true
            try {
                repo.changePassword(current, new)
                messages.tryEmit("Password changed")
                onDone()
            } catch (e: Exception) {
                messages.tryEmit(e.userMessage())
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun PasswordScreen(onBack: () -> Unit) {
    val vm = containerViewModel { PasswordViewModel(it.repository) }
    CollectMessages(vm.messages)
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = { BackTopBar("Change password", onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PasswordField(vm.current, { vm.current = it }, "Current password")
            PasswordField(vm.new, { vm.new = it }, "New password", isError = vm.error != null && vm.confirm.isEmpty())
            PasswordField(
                vm.confirm, { vm.confirm = it }, "Confirm new password",
                isError = vm.error != null, supportingText = vm.error,
                onImeAction = { vm.submit(onBack) },
            )
            Button(
                onClick = { vm.submit(onBack) },
                enabled = !vm.busy && vm.error == null && vm.current.isNotBlank() && vm.new.isNotBlank() && vm.new == vm.confirm,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.busy) "Changing…" else "Change password") }
            Text(
                "You stay signed in on this device. Other sessions keep working until their token expires.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
// endregion

// region Update
class UpdateViewModel(private val repo: CapRoverRepository) : LoadingViewModel<VersionInfo>() {
    var updating by mutableStateOf(false)
        private set

    init { refresh() }
    override suspend fun load() = repo.versionInfo()

    fun update() {
        val latest = data?.latestVersion ?: return
        action(success = "Update to $latest started. CapRover will be unavailable for a minute or two.", reload = false, onSuccess = { updating = true }) {
            repo.performUpdate(latest)
        }
    }
}

@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val vm = containerViewModel { UpdateViewModel(it.repository) }
    val ext = LocalExtendedColors.current
    var confirm by remember { mutableStateOf(false) }

    ServerPage("CapRover version", vm, onBack) { info ->
        SectionCard(
            "Version",
            icon = Icons.Outlined.SystemUpdateAlt,
            trailing = {
                if (info.canUpdate) StatusPill("Update available", ext.warning) else StatusPill("Up to date", ext.running)
            },
        ) {
            InfoRow("Installed", info.currentVersion)
            InfoRow("Latest", info.latestVersion)
            if (info.canUpdate) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { confirm = true }, enabled = !vm.busy && !vm.updating) {
                    Text(if (vm.updating) "Updating…" else "Update to ${info.latestVersion}")
                }
            }
        }
        if (info.changeLogMessage.isNotBlank()) {
            SectionCard("What's new", icon = Icons.Outlined.NewReleases) {
                SelectionContainer { Text(info.changeLogMessage, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Update CapRover?",
            text = "CapRover restarts itself. The dashboard and this app lose connection for a minute or two; your apps keep running. " +
                "Creating a backup first is a good idea.",
            confirmLabel = "Update",
            icon = Icons.Outlined.SystemUpdateAlt,
            onConfirm = vm::update,
            onDismiss = { confirm = false },
        )
    }
}
// endregion

// region Appearance
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val store = LocalContainer.current.settingsStore
    val settings by store.settings.collectAsState()
    Scaffold(topBar = { BackTopBar("Appearance", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard("Theme", icon = Icons.Outlined.Palette) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { store.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.name) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Spacer(Modifier.height(8.dp))
                    SwitchRow(
                        title = "Use wallpaper colours",
                        subtitle = "Material You dynamic colour instead of the CaproverForge blue",
                        checked = settings.dynamicColor,
                        onCheckedChange = store::setDynamicColor,
                    )
                }
            }
        }
    }
}
// endregion
