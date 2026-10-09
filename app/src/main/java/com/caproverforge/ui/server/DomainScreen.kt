package com.caproverforge.ui.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.caproverforge.data.CaptainInfo
import com.caproverforge.data.NginxConfig
import com.caproverforge.ui.appdetail.CodeEditorDialog
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.InfoRow
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.components.SwitchRow
import com.caproverforge.ui.components.TextInputDialog
import com.caproverforge.ui.navigation.containerViewModel
import com.caproverforge.ui.theme.LocalExtendedColors

class DomainViewModel(private val repo: CapRoverRepository) : LoadingViewModel<CaptainInfo>() {
    init { refresh() }
    override suspend fun load() = repo.captainInfo()

    fun changeRootDomain(domain: String, force: Boolean) =
        action(success = "Root domain changed to $domain") { repo.changeRootDomain(domain, force) }

    fun enableSsl(email: String) = action(success = "HTTPS enabled for the dashboard") { repo.enableRootSsl(email) }
    fun setForceSsl(enabled: Boolean) =
        action(success = if (enabled) "HTTPS is now forced" else "HTTP allowed again") { repo.setForceSsl(enabled) }
}

@Composable
fun DomainScreen(onBack: () -> Unit) {
    val vm = containerViewModel { DomainViewModel(it.repository) }
    val ext = LocalExtendedColors.current
    var changeDomain by remember { mutableStateOf(false) }
    var enableSsl by remember { mutableStateOf(false) }

    ServerPage("Domain & HTTPS", vm, onBack) { info ->
        SectionCard("Root domain", icon = Icons.Outlined.Language) {
            InfoRow("Root domain", info.rootDomain.ifBlank { "Not set" })
            InfoRow("Dashboard", "${info.captainSubDomain}.${info.rootDomain}")
            Text(
                "Apps get <app>.${info.rootDomain.ifBlank { "your-domain" }}. A wildcard DNS record (*.${info.rootDomain.ifBlank { "your-domain" }}) must point to this server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { changeDomain = true }, enabled = !vm.busy) { Text("Change root domain") }
        }
        SectionCard(
            "HTTPS",
            icon = Icons.Outlined.Lock,
            trailing = { if (info.hasRootSsl) StatusPill("Enabled", ext.running) else StatusPill("Not enabled", ext.warning) },
        ) {
            if (!info.hasRootSsl) {
                Text(
                    "Enable HTTPS on the dashboard with a free Let's Encrypt certificate. After that, each app can enable HTTPS too.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { enableSsl = true }, enabled = !vm.busy) { Text("Enable HTTPS") }
            } else {
                SwitchRow(
                    title = "Force HTTPS",
                    subtitle = "Redirect HTTP to HTTPS for the dashboard",
                    checked = info.forceSsl,
                    enabled = !vm.busy,
                    onCheckedChange = vm::setForceSsl,
                )
            }
        }
    }

    if (changeDomain) {
        ChangeRootDomainDialog(
            current = vm.data?.rootDomain.orEmpty(),
            onDismiss = { changeDomain = false },
            onChange = vm::changeRootDomain,
        )
    }
    if (enableSsl) {
        TextInputDialog(
            title = "Enable HTTPS",
            label = "Email address",
            supportingText = "Let's Encrypt sends certificate expiry notices here.",
            keyboardType = KeyboardType.Email,
            confirmLabel = "Enable",
            validate = { if (it.contains('@') && it.contains('.')) null else "Enter a valid email address" },
            onConfirm = vm::enableSsl,
            onDismiss = { enableSsl = false },
        )
    }
}

@Composable
private fun ChangeRootDomainDialog(current: String, onDismiss: () -> Unit, onChange: (String, Boolean) -> Unit) {
    var domain by rememberSaveable { mutableStateOf(current) }
    var force by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change root domain") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Every app's default URL changes, and existing HTTPS certificates for the old domain stop working. " +
                        "If you signed in through the old dashboard address you'll need to sign in again with the new one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it.trim().lowercase() },
                    label = { Text("New root domain") },
                    placeholder = { Text("apps.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth().clickable { force = !force }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(force, { force = it })
                    Text("Skip the DNS check", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onChange(domain, force); onDismiss() }, enabled = domain.isNotBlank() && domain != current) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

class NginxViewModel(private val repo: CapRoverRepository) : LoadingViewModel<NginxConfig>() {
    init { refresh() }
    override suspend fun load() = repo.nginxConfig()

    fun save(base: String, captain: String) =
        action(success = "NGINX configuration saved. CapRover is reloading NGINX.") { repo.setNginxConfig(base, captain) }
}

@Composable
fun NginxScreen(onBack: () -> Unit) {
    val vm = containerViewModel { NginxViewModel(it.repository) }
    var editing by remember { mutableStateOf<String?>(null) }

    ServerPage("NGINX", vm, onBack) { config ->
        Text(
            "A broken configuration can take every app offline. CapRover validates it before applying, but keep a copy of what works.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        NginxPart("Base configuration", "nginx.conf shared by all apps", config.baseConfig.customValue) { editing = "base" }
        NginxPart("Dashboard configuration", "Server block for the CapRover dashboard", config.captainConfig.customValue) { editing = "captain" }
    }

    val config = vm.data
    if (config != null) {
        when (editing) {
            "base" -> CodeEditorDialog(
                title = "Base configuration",
                initial = config.baseConfig.customValue?.takeIf { it.isNotBlank() } ?: config.baseConfig.byDefault,
                defaultValue = config.baseConfig.byDefault,
                saveLabel = "Save",
                onSave = { text ->
                    val custom = if (text.trim() == config.baseConfig.byDefault.trim()) "" else text
                    vm.save(custom, config.captainConfig.customValue.orEmpty())
                },
                onDismiss = { editing = null },
            )
            "captain" -> CodeEditorDialog(
                title = "Dashboard configuration",
                initial = config.captainConfig.customValue?.takeIf { it.isNotBlank() } ?: config.captainConfig.byDefault,
                defaultValue = config.captainConfig.byDefault,
                saveLabel = "Save",
                onSave = { text ->
                    val custom = if (text.trim() == config.captainConfig.byDefault.trim()) "" else text
                    vm.save(config.baseConfig.customValue.orEmpty(), custom)
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun NginxPart(title: String, subtitle: String, custom: String?, onEdit: () -> Unit) {
    SectionCard(title, icon = Icons.Outlined.Code, subtitle = subtitle) {
        Text(
            if (custom.isNullOrBlank()) "Using CapRover's default." else "Customised.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onEdit) { Text("Edit") }
    }
}
