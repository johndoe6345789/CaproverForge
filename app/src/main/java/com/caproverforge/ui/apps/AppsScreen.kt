package com.caproverforge.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caproverforge.data.App
import com.caproverforge.data.AppNames
import com.caproverforge.data.AppsData
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.Project
import com.caproverforge.ui.common.AppStatus
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.common.color
import com.caproverforge.ui.common.status
import com.caproverforge.ui.components.BusyBar
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.EmptyState
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.navigation.Navigator
import com.caproverforge.ui.navigation.containerViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class AppsScreenData(val apps: AppsData, val projects: List<Project>)

class AppsViewModel(private val repo: CapRoverRepository) : LoadingViewModel<AppsScreenData>() {
    var query by mutableStateOf("")
    var projectFilter by mutableStateOf<String?>(null)

    init { refresh() }

    override suspend fun load(): AppsScreenData = coroutineScope {
        val apps = async { repo.apps() }
        val projects = async { repo.projects() }
        AppsScreenData(apps.await(), projects.await())
    }

    fun visibleApps(data: AppsScreenData): List<App> = data.apps.apps.filter { app ->
        val q = query.trim()
        val matchesQuery = q.isEmpty() ||
            app.def.appName.contains(q, ignoreCase = true) ||
            app.def.description.orEmpty().contains(q, ignoreCase = true) ||
            app.def.customDomain.any { it.publicDomain.contains(q, ignoreCase = true) } ||
            app.def.tags.any { it.tagName.contains(q, ignoreCase = true) }
        val matchesProject = projectFilter == null || app.def.projectId.orEmpty() == projectFilter
        matchesQuery && matchesProject
    }

    fun createApp(name: String, projectId: String?, persistent: Boolean, onCreated: (String) -> Unit) =
        action(success = "Created $name", onSuccess = { onCreated(name) }) {
            repo.createApp(name, projectId, persistent)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(navigator: Navigator) {
    val vm = containerViewModel { AppsViewModel(it.repository) }
    CollectMessages(vm.messages)
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    // Coming back from an app (deleted, renamed, redeployed): refresh without a spinner.
    LaunchedEffect(Unit) { vm.quietReload() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = vm.query,
                            onValueChange = { vm.query = it },
                            placeholder = { Text("Search apps, domains, tags") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            shape = RoundedCornerShape(28.dp),
                        )
                    },
                    actions = {
                        IconButton(onClick = { searching = false; vm.query = "" }) {
                            Icon(Icons.Outlined.Close, "Close search")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Apps") },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Search") }
                    },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New app") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BusyBar(vm.busy)
            LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }) { data ->
                AppsList(vm, data, onOpen = navigator::openApp, onCreate = { showCreate = true })
            }
        }
    }

    if (showCreate) {
        CreateAppDialog(
            projects = vm.data?.projects.orEmpty(),
            existing = vm.data?.apps?.apps?.map { it.def.appName }.orEmpty().toSet(),
            onDismiss = { showCreate = false },
            onCreate = { name, project, persistent ->
                vm.createApp(name, project, persistent) { navigator.openApp(it) }
            },
        )
    }
}

@Composable
private fun AppsList(vm: AppsViewModel, data: AppsScreenData, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    val apps = vm.visibleApps(data)
    val projectsById = remember(data.projects) { data.projects.associateBy { it.id } }

    if (data.apps.apps.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = "No apps yet",
            body = "Create an app, then deploy a Docker image, a Git repository or a one-click template to it.",
            action = { Button(onClick = onCreate) { Text("Create your first app") } },
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (data.projects.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = vm.projectFilter == null,
                            onClick = { vm.projectFilter = null },
                            label = { Text("All") },
                        )
                    }
                    items(data.projects, key = { it.id }) { project ->
                        FilterChip(
                            selected = vm.projectFilter == project.id,
                            onClick = { vm.projectFilter = if (vm.projectFilter == project.id) null else project.id },
                            label = { Text(project.name) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "${apps.size} of ${data.apps.apps.size} apps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (apps.isEmpty()) {
            item {
                Text(
                    "No apps match your filters.",
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(apps, key = { it.def.appName }) { app ->
            AppCard(
                app = app,
                rootDomain = data.apps.rootDomain,
                projectName = app.def.projectId?.let { projectsById[it]?.name },
                onClick = { onOpen(app.def.appName) },
            )
        }
    }
}

@Composable
private fun AppCard(app: App, rootDomain: String, projectName: String?, onClick: () -> Unit) {
    val def = app.def
    val status = def.status()
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    def.appName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        def.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (def.hasPersistentData) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.Storage, "Persistent data", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val subtitle = when {
                    def.notExposeAsWebApp -> "Internal service · srv-captain--${def.appName}"
                    def.customDomain.isNotEmpty() -> def.customDomain.first().publicDomain +
                        if (def.customDomain.size > 1) " +${def.customDomain.size - 1}" else ""
                    else -> "${def.appName}.$rootDomain"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!def.notExposeAsWebApp && (def.hasDefaultSubDomainSsl || def.customDomain.any { it.hasSsl })) {
                        Icon(Icons.Outlined.Lock, "HTTPS", Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        if (status == AppStatus.Running && def.instanceCount > 1) "Running ×${def.instanceCount}" else status.label,
                        status.color(),
                    )
                    if (def.deployedVersion > 0 || def.versions.isNotEmpty()) {
                        Text("v${def.deployedVersion}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (projectName != null) {
                        Text(
                            projectName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAppDialog(
    projects: List<Project>,
    existing: Set<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, projectId: String?, persistent: Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var persistent by rememberSaveable { mutableStateOf(false) }
    var projectId by rememberSaveable { mutableStateOf<String?>(null) }
    var projectMenu by remember { mutableStateOf(false) }
    val error = when {
        name.isEmpty() -> null
        !AppNames.isValid(name) -> "Lowercase letters, digits and single dashes only."
        name in existing -> "An app with this name already exists."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase().replace(' ', '-') },
                    label = { Text("App name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Becomes part of the default URL.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (projects.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = projectMenu, onExpandedChange = { projectMenu = it }) {
                        OutlinedTextField(
                            value = projects.firstOrNull { it.id == projectId }?.name ?: "No project",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Project") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectMenu) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                            DropdownMenuItem(text = { Text("No project") }, onClick = { projectId = null; projectMenu = false })
                            projects.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name) }, onClick = { projectId = p.id; projectMenu = false })
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { persistent = !persistent },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = persistent, onCheckedChange = { persistent = it })
                    Column {
                        Text("Has persistent data")
                        Text(
                            "Needed for databases and anything that writes to disk. Can't be changed later.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, projectId, persistent); onDismiss() },
                enabled = name.isNotEmpty() && error == null,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
