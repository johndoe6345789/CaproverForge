package com.caproverforge.ui.server

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.DiskCleanupConfig
import com.caproverforge.data.Registries
import com.caproverforge.data.Registry
import com.caproverforge.data.UnusedImage
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.InfoRow
import com.caproverforge.ui.components.PasswordField
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.navigation.containerViewModel
import com.caproverforge.ui.theme.MonoStyle

// region Registries
class RegistriesViewModel(private val repo: CapRoverRepository) : LoadingViewModel<Registries>() {
    init { refresh() }
    override suspend fun load() = repo.registries()

    fun save(registry: Registry, isNew: Boolean) =
        action(success = if (isNew) "Registry added" else "Registry updated") {
            if (isNew) repo.addRegistry(registry) else repo.updateRegistry(registry)
        }

    fun delete(registry: Registry) = action(success = "Registry removed") {
        if (registry.isSelfHosted) repo.disableSelfHostedRegistry() else repo.deleteRegistry(registry.id)
    }

    fun setDefault(id: String) = action(success = "Default push registry updated") { repo.setDefaultPushRegistry(id) }
    fun enableSelfHosted() = action(success = "Self-hosted registry is starting") { repo.enableSelfHostedRegistry() }
}

@Composable
fun RegistriesScreen(onBack: () -> Unit) {
    val vm = containerViewModel { RegistriesViewModel(it.repository) }
    var editing by remember { mutableStateOf<Registry?>(null) }
    var deleting by remember { mutableStateOf<Registry?>(null) }
    var confirmSelfHosted by remember { mutableStateOf(false) }

    ServerPage(
        "Docker registries",
        vm,
        onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Registry(id = "") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add registry") },
            )
        },
    ) { data ->
        Text(
            "Registries let CapRover pull private images, and push images it builds so other cluster nodes can run them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data.registries.none { it.isSelfHosted }) {
            SectionCard("Self-hosted registry", icon = Icons.Outlined.Inventory) {
                Text(
                    "Run a private registry on this server. Needed when you add more nodes and build images with CapRover.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { confirmSelfHosted = true }, enabled = !vm.busy) { Text("Enable self-hosted registry") }
            }
        }
        if (data.registries.isEmpty()) {
            Text("No registries configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        data.registries.forEach { reg ->
            val isDefault = reg.id == data.defaultPushRegistryId
            SectionCard(
                reg.registryDomain.ifBlank { "Registry" },
                icon = Icons.Outlined.Inventory,
                subtitle = if (reg.isSelfHosted) "Self-hosted" else "Remote",
                trailing = { if (isDefault) StatusPill("Default push", MaterialTheme.colorScheme.primary) },
            ) {
                InfoRow("Username", reg.registryUser.ifBlank { "–" })
                InfoRow("Image prefix", reg.registryImagePrefix.ifBlank { "None" }, mono = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!isDefault) {
                        OutlinedButton(onClick = { vm.setDefault(reg.id) }, enabled = !vm.busy) { Text("Use for push") }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!reg.isSelfHosted) {
                        IconButton(onClick = { editing = reg }) { Icon(Icons.Outlined.Edit, "Edit") }
                    }
                    IconButton(onClick = { deleting = reg }) { Icon(Icons.Outlined.Delete, "Remove") }
                }
            }
        }
        Spacer(Modifier.height(72.dp))
    }

    editing?.let { reg ->
        RegistryDialog(reg, onDismiss = { editing = null }) { vm.save(it, isNew = reg.id.isEmpty()) }
    }
    deleting?.let { reg ->
        ConfirmDialog(
            title = if (reg.isSelfHosted) "Disable self-hosted registry?" else "Remove ${reg.registryDomain}?",
            text = "Apps that pull images from it may fail to deploy.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.delete(reg) },
            onDismiss = { deleting = null },
        )
    }
    if (confirmSelfHosted) {
        ConfirmDialog(
            title = "Enable self-hosted registry?",
            text = "CapRover starts a Docker registry on registry.<root domain>. HTTPS must be enabled on the root domain.",
            confirmLabel = "Enable",
            onConfirm = vm::enableSelfHosted,
            onDismiss = { confirmSelfHosted = false },
        )
    }
}

@Composable
private fun RegistryDialog(initial: Registry, onDismiss: () -> Unit, onSave: (Registry) -> Unit) {
    var domain by rememberSaveable { mutableStateOf(initial.registryDomain) }
    var user by rememberSaveable { mutableStateOf(initial.registryUser) }
    var password by rememberSaveable { mutableStateOf(initial.registryPassword) }
    var prefix by rememberSaveable { mutableStateOf(initial.registryImagePrefix) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isEmpty()) "Add registry" else "Edit registry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(domain, { domain = it.trim() }, label = { Text("Registry domain") }, placeholder = { Text("registry.gitlab.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it.trim() }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PasswordField(password, { password = it }, "Password or token")
                OutlinedTextField(
                    prefix, { prefix = it.trim() },
                    label = { Text("Image prefix") },
                    supportingText = { Text("Usually your username or organisation") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(initial.copy(registryDomain = domain, registryUser = user, registryPassword = password, registryImagePrefix = prefix))
                    onDismiss()
                },
                enabled = domain.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
// endregion

// region Disk cleanup
data class DiskCleanupData(val config: DiskCleanupConfig?, val unused: List<UnusedImage>, val keepRecent: Int)

class DiskCleanupViewModel(private val repo: CapRoverRepository) : LoadingViewModel<DiskCleanupData>() {
    var keepRecent by mutableStateOf(2)
    val selected = mutableStateListOf<String>()

    init { refresh() }

    override suspend fun load(): DiskCleanupData {
        val config = runCatching { repo.diskCleanup() }.getOrNull()
        val unused = repo.unusedImages(keepRecent)
        selected.retainAll(unused.map { it.id }.toSet())
        return DiskCleanupData(config, unused, keepRecent)
    }

    fun deleteSelected() {
        val ids = selected.toList()
        action(success = "Deleted ${ids.size} image${if (ids.size == 1) "" else "s"}") {
            repo.deleteImages(ids)
            selected.clear()
        }
    }

    fun saveSchedule(config: DiskCleanupConfig) = action(success = "Automatic cleanup saved") { repo.setDiskCleanup(config) }
}

@Composable
fun DiskCleanupScreen(onBack: () -> Unit) {
    val vm = containerViewModel { DiskCleanupViewModel(it.repository) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editSchedule by remember { mutableStateOf(false) }

    ServerPage("Disk cleanup", vm, onBack) { data ->
        SectionCard("Automatic cleanup", icon = Icons.Outlined.Schedule) {
            val cfg = data.config
            if (cfg == null || cfg.cronSchedule.isBlank()) {
                Text("Not scheduled. Unused images pile up after each deploy.", style = MaterialTheme.typography.bodyMedium)
            } else {
                InfoRow("Schedule (cron)", cfg.cronSchedule, mono = true)
                InfoRow("Timezone", cfg.timezone.ifBlank { "UTC" })
                InfoRow("Keep per app", "${cfg.mostRecentLimit} most recent images")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { editSchedule = true }, enabled = !vm.busy) { Text(if (cfg?.cronSchedule.isNullOrBlank()) "Set up" else "Edit") }
        }

        SectionCard("Unused images", icon = Icons.Outlined.CleaningServices, subtitle = "${data.unused.size} found") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Keep the newest", Modifier.weight(1f))
                listOf(0, 1, 2, 5).forEach { n ->
                    TextButton(onClick = { vm.keepRecent = n; vm.refresh() }) {
                        Text("$n", color = if (vm.keepRecent == n) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Images not used by any running app. The newest ${data.keepRecent} per app are kept so you can roll back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (data.unused.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    val all = vm.selected.size == data.unused.size
                    Checkbox(all, { checked -> vm.selected.clear(); if (checked) vm.selected.addAll(data.unused.map { it.id }) })
                    Text("Select all", Modifier.weight(1f))
                    Button(onClick = { confirmDelete = true }, enabled = vm.selected.isNotEmpty() && !vm.busy) {
                        Text("Delete ${vm.selected.size}")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
            data.unused.forEach { image ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { if (image.id in vm.selected) vm.selected.remove(image.id) else vm.selected.add(image.id) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(image.id in vm.selected, { if (it) vm.selected.add(image.id) else vm.selected.remove(image.id) })
                    Column(Modifier.weight(1f)) {
                        Text(image.tags.firstOrNull() ?: "<untagged>", style = MonoStyle, maxLines = 1)
                        Text(image.id.removePrefix("sha256:").take(12), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${vm.selected.size} images?",
            text = "You won't be able to roll back to versions that used them.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = vm::deleteSelected,
            onDismiss = { confirmDelete = false },
        )
    }
    if (editSchedule) {
        CleanupScheduleDialog(vm.data?.config ?: DiskCleanupConfig(), { editSchedule = false }, vm::saveSchedule)
    }
}

@Composable
private fun CleanupScheduleDialog(initial: DiskCleanupConfig, onDismiss: () -> Unit, onSave: (DiskCleanupConfig) -> Unit) {
    var cron by rememberSaveable { mutableStateOf(initial.cronSchedule.ifBlank { "0 3 * * 0" }) }
    var tz by rememberSaveable { mutableStateOf(initial.timezone.ifBlank { java.util.TimeZone.getDefault().id }) }
    var keep by rememberSaveable { mutableStateOf(initial.mostRecentLimit.takeIf { it > 0 }?.toString() ?: "2") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Automatic cleanup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    cron, { cron = it },
                    label = { Text("Cron schedule") },
                    supportingText = { Text("Default: every Sunday at 03:00. Leave empty to disable.") },
                    singleLine = true, textStyle = MonoStyle, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(tz, { tz = it.trim() }, label = { Text("Timezone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    keep, { keep = it.filter(Char::isDigit).take(3) },
                    label = { Text("Images to keep per app") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(DiskCleanupConfig(mostRecentLimit = keep.toIntOrNull()?.coerceAtLeast(1) ?: 1, cronSchedule = cron.trim(), timezone = tz.ifBlank { "UTC" }))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
// endregion
