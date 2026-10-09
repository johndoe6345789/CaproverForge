package com.caproverforge.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caproverforge.data.AppDefinition
import com.caproverforge.data.EnvVar
import com.caproverforge.data.EnvVarsText
import com.caproverforge.data.PortMapping
import com.caproverforge.data.Volume
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigTab(vm: AppDetailViewModel, data: AppDetailData, def: AppDefinition) {
    var bulkEnv by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.validationError()?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        SectionCard(
            "Environment variables",
            icon = Icons.Outlined.DataObject,
            subtitle = "${def.envVars.size} defined",
            trailing = {
                TextButton(onClick = { bulkEnv = !bulkEnv }) { Text(if (bulkEnv) "List" else "Bulk edit") }
            },
        ) {
            if (bulkEnv) {
                var text by remember { mutableStateOf(EnvVarsText.format(def.envVars)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        vm.edit { d -> d.copy(envVars = EnvVarsText.parse(it)) }
                    },
                    textStyle = MonoStyle,
                    placeholder = { Text("KEY=value", style = MonoStyle) },
                    supportingText = { Text("One KEY=value per line. Lines starting with # are ignored.") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                def.envVars.forEachIndexed { index, env ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = env.key,
                            onValueChange = { v -> vm.edit { d -> d.copy(envVars = d.envVars.replace(index, env.copy(key = v.trim()))) } },
                            label = { Text("Key") },
                            singleLine = true,
                            textStyle = MonoStyle,
                            modifier = Modifier.weight(0.42f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = env.value,
                            onValueChange = { v -> vm.edit { d -> d.copy(envVars = d.envVars.replace(index, env.copy(value = v))) } },
                            label = { Text("Value") },
                            singleLine = true,
                            textStyle = MonoStyle,
                            modifier = Modifier.weight(0.58f),
                        )
                        IconButton(onClick = { vm.edit { d -> d.copy(envVars = d.envVars.removedAt(index)) } }) {
                            Icon(Icons.Outlined.Close, "Remove ${env.key}")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                AddButton("Add variable") { vm.edit { d -> d.copy(envVars = d.envVars + EnvVar()) } }
            }
        }

        SectionCard("Port mappings", icon = Icons.Outlined.Lan, subtitle = "Publish container ports directly on the server") {
            def.ports.forEachIndexed { index, port ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Server", port.hostPort, Modifier.weight(1f)) { v ->
                        vm.edit { d -> d.copy(ports = d.ports.replace(index, port.copy(hostPort = v))) }
                    }
                    Text("→", Modifier.padding(horizontal = 6.dp))
                    NumberField("Container", port.containerPort, Modifier.weight(1f)) { v ->
                        vm.edit { d -> d.copy(ports = d.ports.replace(index, port.copy(containerPort = v))) }
                    }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        val next = if (port.protocol == "udp") "tcp" else "udp"
                        vm.edit { d -> d.copy(ports = d.ports.replace(index, port.copy(protocol = next))) }
                    }) { Text((port.protocol ?: "tcp").uppercase()) }
                    IconButton(onClick = { vm.edit { d -> d.copy(ports = d.ports.removedAt(index)) } }) {
                        Icon(Icons.Outlined.Close, "Remove port")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            AddButton("Add port mapping") { vm.edit { d -> d.copy(ports = d.ports + PortMapping(protocol = "tcp")) } }
        }

        if (def.hasPersistentData) {
            SectionCard("Persistent directories", icon = Icons.Outlined.Storage, subtitle = "Data here survives restarts and redeploys") {
                def.volumes.forEachIndexed { index, volume ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    VolumeEditor(volume,
                        onChange = { v -> vm.edit { d -> d.copy(volumes = d.volumes.replace(index, v)) } },
                        onRemove = { vm.edit { d -> d.copy(volumes = d.volumes.removedAt(index)) } },
                    )
                }
                AddButton("Add persistent directory") { vm.edit { d -> d.copy(volumes = d.volumes + Volume()) } }
            }

            if (data.nodes.size > 1) {
                SectionCard("Placement", icon = Icons.Outlined.DeveloperBoard) {
                    var open by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                        OutlinedTextField(
                            value = data.nodes.firstOrNull { it.nodeId == def.nodeId }?.hostname ?: "Any node",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Run on node") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Any node") }, onClick = { vm.edit { it.copy(nodeId = "") }; open = false })
                            data.nodes.forEach { n ->
                                DropdownMenuItem(
                                    text = { Text("${n.hostname} (${n.type})") },
                                    onClick = { vm.edit { it.copy(nodeId = n.nodeId) }; open = false },
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard("Advanced", icon = Icons.Outlined.Tune) {
            OutlinedTextField(
                value = def.captainDefinitionRelativeFilePath,
                onValueChange = { v -> vm.edit { it.copy(captainDefinitionRelativeFilePath = v) } },
                label = { Text("captain-definition path") },
                supportingText = { Text("Relative to the repository or tarball root") },
                singleLine = true,
                textStyle = MonoStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            EditorLink(
                "Service update override",
                if (def.serviceUpdateOverride.isNullOrBlank()) "Not set" else "Custom Docker service spec (YAML)",
            ) { editor = "override" }
            EditorLink(
                "Pre-deploy script",
                if (def.preDeployFunction.isNullOrBlank()) "Not set" else "Runs before each deploy",
            ) { editor = "predeploy" }
        }
    }

    when (editor) {
        "override" -> CodeEditorDialog(
            title = "Service update override",
            initial = def.serviceUpdateOverride.orEmpty(),
            hint = "YAML or JSON merged into the Docker service update spec. Use with care.",
            onSave = { v -> vm.edit { it.copy(serviceUpdateOverride = v) } },
            onDismiss = { editor = null },
        )
        "predeploy" -> CodeEditorDialog(
            title = "Pre-deploy script",
            initial = def.preDeployFunction.orEmpty(),
            hint = "JavaScript function body run by CapRover before deploying a new version.",
            onSave = { v -> vm.edit { it.copy(preDeployFunction = v) } },
            onDismiss = { editor = null },
        )
    }
}

@Composable
private fun VolumeEditor(volume: Volume, onChange: (Volume) -> Unit, onRemove: () -> Unit) {
    var bind by remember(volume.isBind) { mutableStateOf(volume.isBind) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                SegmentedButton(
                    selected = !bind,
                    onClick = { bind = false; onChange(volume.copy(hostPath = null)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Volume") }
                SegmentedButton(
                    selected = bind,
                    onClick = { bind = true; onChange(volume.copy(volumeName = null, hostPath = volume.hostPath ?: "")) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Host path") }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove directory") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = volume.containerPath,
            onValueChange = { onChange(volume.copy(containerPath = it.trim())) },
            label = { Text("Path in container") },
            placeholder = { Text("/var/lib/data") },
            singleLine = true,
            textStyle = MonoStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        if (bind) {
            OutlinedTextField(
                value = volume.hostPath.orEmpty(),
                onValueChange = { onChange(volume.copy(hostPath = it.trim())) },
                label = { Text("Path on server") },
                placeholder = { Text("/srv/data") },
                singleLine = true,
                textStyle = MonoStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = volume.volumeName.orEmpty(),
                onValueChange = { onChange(volume.copy(volumeName = it.trim())) },
                label = { Text("Volume name") },
                singleLine = true,
                textStyle = MonoStyle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

@Composable
private fun EditorLink(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClick) { Icon(Icons.Outlined.EditNote, "Edit $title") }
    }
}

internal fun <T> List<T>.replace(index: Int, item: T): List<T> = toMutableList().also { it[index] = item }
internal fun <T> List<T>.removedAt(index: Int): List<T> = toMutableList().also { it.removeAt(index) }
