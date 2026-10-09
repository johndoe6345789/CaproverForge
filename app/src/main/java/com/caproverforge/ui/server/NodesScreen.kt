package com.caproverforge.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.Format
import com.caproverforge.data.NodeInfo
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.InfoRow
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.navigation.containerViewModel
import com.caproverforge.ui.theme.LocalExtendedColors
import com.caproverforge.ui.theme.MonoStyle

class NodesViewModel(private val repo: CapRoverRepository) : LoadingViewModel<List<NodeInfo>>() {
    init { refresh() }
    override suspend fun load() = repo.nodes().sortedWith(compareByDescending<NodeInfo> { it.isLeader }.thenBy { it.hostname })

    fun addNode(type: String, key: String, remoteIp: String, port: String, user: String, captainIp: String) =
        action(success = "Node joined the cluster") { repo.addNode(type, key, remoteIp, port, user, captainIp) }
}

@Composable
fun NodesScreen(onBack: () -> Unit) {
    val vm = containerViewModel { NodesViewModel(it.repository) }
    var adding by remember { mutableStateOf(false) }
    val ext = LocalExtendedColors.current

    ServerPage(
        "Cluster nodes",
        vm,
        onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add node") })
        },
    ) { nodes ->
        nodes.forEach { node ->
            SectionCard(
                node.hostname,
                icon = Icons.Outlined.Dns,
                subtitle = if (node.isLeader) "Leader · ${node.type}" else node.type.replaceFirstChar { it.uppercase() },
                trailing = {
                    val ready = node.status.equals("ready", true)
                    StatusPill(node.status.ifBlank { node.state }.replaceFirstChar { it.uppercase() }, if (ready) ext.running else MaterialTheme.colorScheme.error)
                },
            ) {
                InfoRow("IP address", node.ip, mono = true)
                InfoRow("Resources", "${Format.cpus(node.nanoCpu)} · ${Format.bytes(node.memoryBytes)} RAM")
                InfoRow("System", "${node.operatingSystem} · ${node.architecture}")
                InfoRow("Docker", node.dockerEngineVersion)
                InfoRow("Node ID", node.nodeId, mono = true)
            }
        }
        Spacer(Modifier.height(72.dp))
    }

    if (adding) {
        AddNodeDialog(
            leaderIp = vm.data?.firstOrNull { it.isLeader }?.ip.orEmpty(),
            onDismiss = { adding = false },
            onAdd = vm::addNode,
        )
    }
}

@Composable
private fun AddNodeDialog(
    leaderIp: String,
    onDismiss: () -> Unit,
    onAdd: (type: String, key: String, remoteIp: String, port: String, user: String, captainIp: String) -> Unit,
) {
    var manager by rememberSaveable { mutableStateOf(false) }
    var remoteIp by rememberSaveable { mutableStateOf("") }
    var captainIp by rememberSaveable { mutableStateOf(leaderIp) }
    var user by rememberSaveable { mutableStateOf("root") }
    var port by rememberSaveable { mutableStateOf("22") }
    var key by rememberSaveable { mutableStateOf("") }
    val valid = remoteIp.isNotBlank() && captainIp.isNotBlank() && user.isNotBlank() && port.toIntOrNull() != null && key.contains("PRIVATE KEY")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add node") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "CapRover connects over SSH and joins the server to the swarm. " +
                        "Docker must already be installed on it, and ports 2377, 7946 and 4789 must be open between servers.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!manager, { manager = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Worker") }
                    SegmentedButton(manager, { manager = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Manager") }
                }
                OutlinedTextField(remoteIp, { remoteIp = it.trim() }, label = { Text("New node IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    captainIp, { captainIp = it.trim() },
                    label = { Text("This server's IP") },
                    supportingText = { Text("Address the new node uses to reach the leader") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(user, { user = it.trim() }, label = { Text("SSH user") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.6f),
                    )
                }
                OutlinedTextField(
                    key, { key = it },
                    label = { Text("SSH private key") },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", style = MonoStyle) },
                    textStyle = MonoStyle, minLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onAdd(if (manager) "manager" else "worker", key, remoteIp, port, user, captainIp)
                onDismiss()
            }, enabled = valid) { Text("Join cluster") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
