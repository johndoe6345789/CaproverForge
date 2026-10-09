package com.caproverforge.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caproverforge.data.AppDefinition
import com.caproverforge.data.AppTag
import com.caproverforge.data.Format
import com.caproverforge.ui.common.color
import com.caproverforge.ui.common.defaultUrl
import com.caproverforge.ui.common.status
import com.caproverforge.ui.components.InfoRow
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.components.TextInputDialog
import com.caproverforge.ui.components.openUrl
import com.caproverforge.ui.components.rememberCopy
import com.caproverforge.ui.theme.MonoStyle

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewTab(vm: AppDetailViewModel, data: AppDetailData, def: AppDefinition, onOpenTab: (Int) -> Unit) {
    val context = LocalContext.current
    val copy = rememberCopy()
    val server = data.def
    val status = server.status()
    var editDescription by remember { mutableStateOf(false) }
    var addTag by remember { mutableStateOf(false) }
    val currentVersion = server.versions.firstOrNull { it.version == server.deployedVersion }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard("Status", icon = Icons.Outlined.Speed, trailing = { StatusPill(status.label, status.color()) }) {
            InfoRow("Deployed version", if (server.versions.isEmpty()) "Nothing deployed yet" else "v${server.deployedVersion}")
            server.deployedImage?.let { image ->
                InfoRow("Image", image, mono = true, trailing = {
                    IconButton(onClick = { copy("Image name", image) }) { Icon(Icons.Outlined.ContentCopy, "Copy image name") }
                })
            }
            currentVersion?.timeStamp?.takeIf { it.isNotBlank() }?.let {
                InfoRow("Deployed", "${Format.relative(it)} · ${Format.absolute(it)}")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Instances", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (def.instanceCount == 0) "Stopped — scale up to start" else "Containers running this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(
                    onClick = { vm.edit { it.copy(instanceCount = (it.instanceCount - 1).coerceAtLeast(0)) } },
                    enabled = def.instanceCount > 0,
                ) { Icon(Icons.Outlined.Remove, "Fewer instances") }
                Text(
                    "${def.instanceCount}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                FilledTonalIconButton(
                    onClick = { vm.edit { it.copy(instanceCount = it.instanceCount + 1) } },
                ) { Icon(Icons.Outlined.Add, "More instances") }
            }
            if (def.hasPersistentData && def.instanceCount > 1) {
                Text(
                    "Several instances of an app with persistent data can write to the same files and corrupt them. Only do this if the app supports it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            QuickAction("Deploy", Icons.Outlined.RocketLaunch, Modifier.weight(1f)) { onOpenTab(3) }
            QuickAction("Logs", Icons.Outlined.Terminal, Modifier.weight(1f)) { onOpenTab(4) }
            QuickAction("Config", Icons.Outlined.Settings, Modifier.weight(1f)) { onOpenTab(2) }
        }

        SectionCard("Endpoints", icon = Icons.Outlined.Link) {
            if (!server.notExposeAsWebApp) {
                val url = server.defaultUrl(data.rootDomain)
                InfoRow("Default URL", url.substringAfter("://"), onClick = { openUrl(context, url) }, trailing = {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
                })
                server.customDomain.forEach { d ->
                    val u = "${if (d.hasSsl) "https" else "http"}://${d.publicDomain}"
                    InfoRow("Custom domain", d.publicDomain, onClick = { openUrl(context, u) }, trailing = {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
                    })
                }
            }
            val internal = "srv-captain--${server.appName}"
            InfoRow("Internal hostname", internal, mono = true, trailing = {
                IconButton(onClick = { copy("Internal hostname", internal) }) { Icon(Icons.Outlined.ContentCopy, "Copy hostname") }
            })
            if (!server.notExposeAsWebApp) {
                InfoRow("Container HTTP port", "${server.containerHttpPort ?: 80}")
            }
            server.ports.forEach { p ->
                InfoRow("Host port", "${p.hostPort} → ${p.containerPort}/${p.protocol ?: "tcp"}")
            }
        }

        SectionCard("Details", icon = Icons.Outlined.Info) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Outlined.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp).size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text("Description", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(def.description?.takeIf { it.isNotBlank() } ?: "No description", style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = { editDescription = true }) { Icon(Icons.Outlined.Edit, "Edit description") }
            }
            if (data.projects.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = data.projects.firstOrNull { it.id == def.projectId }?.name ?: "No project",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Project") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text("No project") }, onClick = { vm.edit { it.copy(projectId = "") }; open = false })
                        data.projects.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.edit { it.copy(projectId = p.id) }; open = false })
                        }
                    }
                }
            }
            Text(
                "Tags",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                def.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { vm.edit { d -> d.copy(tags = d.tags - tag) } },
                        label = { Text(tag.tagName) },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove tag", Modifier.size(16.dp)) },
                    )
                }
                AssistChip(onClick = { addTag = true }, label = { Text("Add tag") }, leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)) })
            }
            InfoRow("Persistent data", if (server.hasPersistentData) "Yes" else "No")
        }
    }

    if (editDescription) {
        TextInputDialog(
            title = "Description",
            label = "Notes about this app",
            initial = def.description.orEmpty(),
            singleLine = false,
            onConfirm = { text -> vm.edit { it.copy(description = text) } },
            onDismiss = { editDescription = false },
        )
    }
    if (addTag) {
        TextInputDialog(
            title = "Add tag",
            label = "Tag",
            validate = { t -> if (Regex("^[a-z0-9][a-z0-9_-]*$").matches(t.lowercase())) null else "Letters, digits, - and _ only." },
            onConfirm = { text ->
                vm.edit { d -> if (d.tags.any { it.tagName == text.lowercase() }) d else d.copy(tags = d.tags + AppTag(text.lowercase())) }
            },
            onDismiss = { addTag = false },
        )
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

/** Shared by the tabs: a mono label/value line. */
@Composable
internal fun MonoText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MonoStyle, modifier = modifier)
}

@Composable
internal fun SmallTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled) { Text(text) }
}
