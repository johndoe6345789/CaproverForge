package com.caproverforge.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caproverforge.data.AppDefinition
import com.caproverforge.data.HttpAuth
import com.caproverforge.ui.common.defaultUrl
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.PasswordField
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.components.SwitchRow
import com.caproverforge.ui.components.TextInputDialog
import com.caproverforge.ui.components.openUrl
import com.caproverforge.ui.theme.LocalExtendedColors
import com.caproverforge.ui.theme.MonoStyle

private val domainRegex = Regex("^(?=.{1,253}$)(\\*\\.)?([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HttpTab(vm: AppDetailViewModel, data: AppDetailData, def: AppDefinition) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val server = data.def
    var addDomain by remember { mutableStateOf(false) }
    var removeDomain by remember { mutableStateOf<String?>(null) }
    var editNginx by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (def.notExposeAsWebApp) {
            SectionCard("Internal service", icon = Icons.Outlined.VisibilityOff) {
                Text(
                    "This app has no public URL. Other apps reach it at srv-captain--${def.appName}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SwitchRow(
                    title = "Expose as web app",
                    subtitle = "Serve it through the NGINX load balancer on a domain",
                    checked = false,
                    onCheckedChange = { vm.edit { it.copy(notExposeAsWebApp = false) } },
                )
            }
            return@Column
        }

        SectionCard("Domains", icon = Icons.Outlined.Language) {
            val url = server.defaultUrl(data.rootDomain)
            DomainRow(
                domain = "${server.appName}.${data.rootDomain}",
                hasSsl = server.hasDefaultSubDomainSsl,
                label = "Default",
                onOpen = { openUrl(context, url) },
                onEnableSsl = vm::enableBaseSsl,
                busy = vm.busy,
            )
            server.customDomain.forEach { d ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DomainRow(
                    domain = d.publicDomain,
                    hasSsl = d.hasSsl,
                    label = null,
                    onOpen = { openUrl(context, "${if (d.hasSsl) "https" else "http"}://${d.publicDomain}") },
                    onEnableSsl = { vm.enableDomainSsl(d.publicDomain) },
                    onRemove = { removeDomain = d.publicDomain },
                    busy = vm.busy,
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { addDomain = true }, enabled = !vm.busy) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Text("  Connect domain")
            }
            Text(
                "Point the domain's DNS (A record) at this server before connecting it. " +
                    "HTTPS certificates come from Let's Encrypt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SectionCard("Routing", icon = Icons.Outlined.Tune) {
            val anySsl = server.hasDefaultSubDomainSsl || server.customDomain.any { it.hasSsl }
            SwitchRow(
                title = "Force HTTPS",
                subtitle = if (anySsl) "Redirect all HTTP traffic to HTTPS" else "Enable HTTPS on a domain first",
                checked = def.forceSsl,
                enabled = anySsl || def.forceSsl,
                onCheckedChange = { v -> vm.edit { it.copy(forceSsl = v) } },
            )
            SwitchRow(
                title = "WebSocket support",
                subtitle = "Pass Upgrade headers through NGINX",
                checked = def.websocketSupport,
                onCheckedChange = { v -> vm.edit { it.copy(websocketSupport = v) } },
            )
            SwitchRow(
                title = "Do not expose as web app",
                subtitle = "For databases and other internal services",
                checked = def.notExposeAsWebApp,
                onCheckedChange = { v -> vm.edit { it.copy(notExposeAsWebApp = v) } },
            )
            OutlinedTextField(
                value = def.containerHttpPort?.toString().orEmpty(),
                onValueChange = { v ->
                    val digits = v.filter { it.isDigit() }.take(5)
                    vm.edit { it.copy(containerHttpPort = digits.toIntOrNull()) }
                },
                label = { Text("Container HTTP port") },
                placeholder = { Text("80") },
                supportingText = { Text("The port your app listens on inside the container") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            val domains = listOf("${server.appName}.${data.rootDomain}") + server.customDomain.map { it.publicDomain }
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = def.redirectDomain?.takeIf { it.isNotBlank() } ?: "No redirect",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Redirect all domains to") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(text = { Text("No redirect") }, onClick = { vm.edit { it.copy(redirectDomain = "") }; open = false })
                    domains.forEach { d ->
                        DropdownMenuItem(text = { Text(d) }, onClick = { vm.edit { it.copy(redirectDomain = d) }; open = false })
                    }
                }
            }
        }

        SectionCard("HTTP basic auth", icon = Icons.Outlined.Password, subtitle = "Ask for a username and password before showing the app") {
            val auth = def.httpAuth
            SwitchRow(
                title = "Require login",
                checked = auth != null,
                onCheckedChange = { on ->
                    vm.edit { it.copy(httpAuth = if (on) (server.httpAuth ?: HttpAuth(user = "admin")) else null) }
                },
            )
            if (auth != null) {
                OutlinedTextField(
                    value = auth.user,
                    onValueChange = { v -> vm.edit { it.copy(httpAuth = auth.copy(user = v)) } },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    value = auth.password.orEmpty(),
                    onValueChange = { v -> vm.edit { it.copy(httpAuth = auth.copy(password = v.ifEmpty { null })) } },
                    label = if (auth.passwordHashed.isNullOrBlank()) "Password" else "New password",
                    supportingText = if (auth.passwordHashed.isNullOrBlank()) null else "Leave empty to keep the current password",
                    isError = auth.passwordHashed.isNullOrBlank() && auth.password.isNullOrBlank(),
                )
            }
        }

        SectionCard("NGINX configuration", icon = Icons.Outlined.Code) {
            Text(
                if (def.customNginxConfig.isNullOrBlank()) "Using CapRover's default configuration."
                else "This app uses a customised NGINX configuration.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { editNginx = true }) { Text("Edit configuration") }
        }
    }

    if (addDomain) {
        TextInputDialog(
            title = "Connect domain",
            label = "Domain",
            placeholder = "www.example.com",
            supportingText = "The DNS record must already point to this server.",
            confirmLabel = "Connect",
            keyboardType = KeyboardType.Uri,
            validate = { if (domainRegex.matches(it.trim().lowercase())) null else "Enter a domain like app.example.com" },
            onConfirm = { vm.addDomain(it.lowercase()) },
            onDismiss = { addDomain = false },
        )
    }
    removeDomain?.let { domain ->
        ConfirmDialog(
            title = "Remove $domain?",
            text = "The app will stop answering on this domain.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.removeDomain(domain) },
            onDismiss = { removeDomain = null },
        )
    }
    if (editNginx) {
        CodeEditorDialog(
            title = "NGINX configuration",
            initial = def.customNginxConfig?.takeIf { it.isNotBlank() } ?: data.defaultNginxConfig,
            defaultValue = data.defaultNginxConfig,
            hint = "Template variables like <%-s.publicDomain%> are filled in by CapRover.",
            onSave = { text ->
                val custom = if (text.trim() == data.defaultNginxConfig.trim()) "" else text
                vm.edit { it.copy(customNginxConfig = custom) }
            },
            onDismiss = { editNginx = false },
        )
    }
}

@Composable
private fun DomainRow(
    domain: String,
    hasSsl: Boolean,
    label: String?,
    onOpen: () -> Unit,
    onEnableSsl: () -> Unit,
    busy: Boolean,
    onRemove: (() -> Unit)? = null,
) {
    val ext = LocalExtendedColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(domain, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (label != null) StatusPill(label, MaterialTheme.colorScheme.primary)
                    if (hasSsl) StatusPill("HTTPS", ext.running) else StatusPill("HTTP only", ext.warning)
                }
            }
            IconButton(onClick = onOpen) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open $domain") }
            if (onRemove != null) {
                IconButton(onClick = onRemove, enabled = !busy) { Icon(Icons.Outlined.Delete, "Remove $domain") }
            }
        }
        if (!hasSsl) {
            TextButton(onClick = onEnableSsl, enabled = !busy) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp))
                Text("  Enable HTTPS")
            }
        }
    }
}

/** Full-screen monospace editor used for NGINX configs, Dockerfiles, YAML overrides. */
@Composable
internal fun CodeEditorDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    defaultValue: String? = null,
    hint: String? = null,
    saveLabel: String = "Done",
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                BackTopBar(title = title, onBack = onDismiss, actions = {
                    if (defaultValue != null) TextButton(onClick = { text = defaultValue }) { Text("Reset") }
                    Button(onClick = { onSave(text); onDismiss() }, modifier = Modifier.padding(end = 8.dp)) { Text(saveLabel) }
                })
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().imePadding().padding(12.dp)) {
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MonoStyle,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
