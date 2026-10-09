package com.caproverforge.ui.appdetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caproverforge.data.AppNames
import com.caproverforge.ui.common.AppStatus
import com.caproverforge.ui.common.defaultUrl
import com.caproverforge.ui.common.status
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.BusyBar
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.openUrl
import com.caproverforge.ui.navigation.containerViewModel

private val tabTitles = listOf("Status", "HTTP", "Config", "Deploy", "Logs")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(appName: String, onBack: () -> Unit) {
    val vm = containerViewModel(key = "app:$appName") { AppDetailViewModel(it.repository, appName) }
    CollectMessages(vm.messages)
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val data = vm.data
    val def = vm.draft ?: data?.def

    BackHandler(enabled = vm.hasChanges) { confirmLeave = true }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            Column {
                BackTopBar(
                    title = appName,
                    subtitle = data?.def?.status()?.let { status ->
                        if (status == AppStatus.Running) "Running · ${data.def.instanceCount} instance${if (data.def.instanceCount == 1) "" else "s"}"
                        else status.label
                    },
                    onBack = { if (vm.hasChanges) confirmLeave = true else onBack() },
                    actions = {
                        if (data != null && !data.def.notExposeAsWebApp) {
                            IconButton(onClick = { openUrl(context, data.def.defaultUrl(data.rootDomain)) }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open app")
                            }
                        }
                        IconButton(onClick = { menu = true }, enabled = data != null) {
                            Icon(Icons.Outlined.MoreVert, "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Restart") },
                                leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                                onClick = { menu = false; confirmRestart = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                onClick = { menu = false; showRename = true },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; showDelete = true },
                            )
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    tabTitles.forEachIndexed { i, title ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, maxLines = 1, softWrap = false) })
                    }
                }
                BusyBar(vm.busy)
            }
        },
        bottomBar = {
            AnimatedVisibility(vm.hasChanges && tab != 3 && tab != 4, enter = expandVertically(), exit = shrinkVertically()) {
                UnsavedChangesBar(busy = vm.busy, onDiscard = vm::discardChanges, onSave = vm::save)
            }
        },
    ) { padding ->
        LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }, Modifier.padding(padding)) { loaded ->
            val current = def ?: loaded.def
            when (tab) {
                0 -> OverviewTab(vm, loaded, current, onOpenTab = { tab = it })
                1 -> HttpTab(vm, loaded, current)
                2 -> ConfigTab(vm, loaded, current)
                3 -> DeployTab(vm, loaded)
                else -> LogsTab(vm)
            }
        }
    }

    if (confirmRestart) {
        ConfirmDialog(
            title = "Restart $appName?",
            text = "All running containers of this app will be replaced. Expect a few seconds of downtime." +
                if (vm.hasChanges) "\n\nYour unsaved changes are not included; use Save & restart for that." else "",
            confirmLabel = "Restart",
            icon = Icons.Outlined.RestartAlt,
            onConfirm = vm::restart,
            onDismiss = { confirmRestart = false },
        )
    }
    if (showRename) {
        RenameDialog(appName, onDismiss = { showRename = false }) { newName ->
            vm.rename(newName) { onBack() }
        }
    }
    if (showDelete && data != null) {
        DeleteAppDialog(
            appName = appName,
            volumes = data.def.volumes.mapNotNull { it.volumeName?.takeIf { n -> n.isNotBlank() } },
            onDismiss = { showDelete = false },
            onDelete = { volumes -> vm.delete(volumes, onDeleted = onBack) },
        )
    }
    if (confirmLeave) {
        ConfirmDialog(
            title = "Discard changes?",
            text = "You have unsaved changes to $appName.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = { vm.discardChanges(); onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun UnsavedChangesBar(busy: Boolean, onDiscard: () -> Unit, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Unsaved changes", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onDiscard, enabled = !busy) { Text("Discard") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSave, enabled = !busy) { Text("Save & restart") }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    val error = when {
        name == current -> null
        !AppNames.isValid(name) -> "Lowercase letters, digits and single dashes only."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The default domain and the internal hostname (srv-captain--name) change too. " +
                        "Other apps that connect to this one by name must be updated.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.lowercase() },
                    label = { Text("New name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onRename(name); onDismiss() }, enabled = name != current && error == null) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteAppDialog(
    appName: String,
    volumes: List<String>,
    onDismiss: () -> Unit,
    onDelete: (List<String>) -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete $appName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This removes the app and its containers. It can't be undone.")
                if (volumes.isNotEmpty()) {
                    Text("Also delete these volumes (their data is lost):", style = MaterialTheme.typography.labelLarge)
                    volumes.forEach { v ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (v in selected) selected.remove(v) else selected.add(v) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = v in selected, onCheckedChange = { if (it) selected.add(v) else selected.remove(v) })
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type $appName to confirm") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onDelete(selected.toList()); onDismiss() },
                enabled = typed == appName,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

