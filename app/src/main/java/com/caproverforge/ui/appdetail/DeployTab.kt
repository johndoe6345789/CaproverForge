package com.caproverforge.ui.appdetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caproverforge.data.AppVersion
import com.caproverforge.data.DeployTokenConfig
import com.caproverforge.data.Format
import com.caproverforge.data.PushWebhook
import com.caproverforge.data.RepoInfo
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.PollingEffect
import com.caproverforge.ui.components.LogViewer
import com.caproverforge.ui.components.PasswordField
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.components.SwitchRow
import com.caproverforge.ui.components.rememberCopy
import com.caproverforge.ui.navigation.LocalContainer
import com.caproverforge.ui.theme.LocalExtendedColors
import com.caproverforge.ui.theme.MonoStyle

private val methods = listOf("Image", "Git", "Upload", "Dockerfile", "captain-definition")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeployTab(vm: AppDetailViewModel, data: AppDetailData) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val copy = rememberCopy()
    val def = vm.draft ?: data.def
    val building = vm.buildLogs?.isAppBuilding == true || data.def.isAppBuilding
    var method by rememberSaveable { mutableIntStateOf(if (data.def.appPushWebhook?.repoInfo != null) 1 else 0) }
    var rollbackTo by remember { mutableStateOf<AppVersion?>(null) }
    var pendingUpload by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickTar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingUpload = uri }

    PollingEffect(intervalMs = { if (vm.buildLogs?.isAppBuilding == true) 2_000 else 8_000 }) { vm.fetchBuildLogs() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            "Build",
            icon = Icons.Outlined.Build,
            trailing = {
                when {
                    building -> StatusPill("Building", ext.warning)
                    vm.buildLogs?.isBuildFailed == true -> StatusPill("Last build failed", MaterialTheme.colorScheme.error)
                    vm.buildLogs != null -> StatusPill("Idle", ext.running)
                }
            },
        ) {
            if (building) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Building and deploying… logs update live.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            LogViewer(
                lines = vm.buildLogLines(),
                wrap = true,
                emptyText = if (vm.buildLogs == null) "Loading build logs…" else "No build output yet.",
                modifier = Modifier.heightIn(min = 120.dp, max = 320.dp),
            )
        }

        SectionCard("Deploy a new version", icon = Icons.Outlined.RocketLaunch) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEachIndexed { i, label ->
                    FilterChip(selected = method == i, onClick = { method = i }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (method) {
                0 -> ImageDeploy(vm.busy || building) { vm.deployImage(it) }
                1 -> GitDeploy(vm, data)
                2 -> {
                    Text(
                        "Upload a .tar archive of your source code. It must contain a captain-definition file (or a Dockerfile).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { pickTar.launch(arrayOf("application/x-tar", "application/gzip", "application/octet-stream", "*/*")) }, enabled = !vm.busy && !building) {
                        Icon(Icons.Outlined.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose tarball")
                    }
                }
                3 -> CodeDeploy(
                    label = "Dockerfile",
                    placeholder = "FROM nginx:alpine\nCOPY . /usr/share/nginx/html",
                    busy = vm.busy || building,
                ) { vm.deployDockerfile(it) }
                else -> CodeDeploy(
                    label = "captain-definition (JSON)",
                    placeholder = "{\n  \"schemaVersion\": 2,\n  \"imageName\": \"nginx:alpine\"\n}",
                    busy = vm.busy || building,
                ) { vm.deployCaptainDefinition(it) }
            }
        }

        SectionCard("Deploy token", icon = Icons.Outlined.Key, subtitle = "For CI pipelines and the caprover CLI") {
            val token = def.appDeployTokenConfig
            SwitchRow(
                title = "Enable app token",
                subtitle = "Lets the CLI deploy this app without your password",
                checked = token?.enabled == true,
                onCheckedChange = { on ->
                    vm.edit { it.copy(appDeployTokenConfig = DeployTokenConfig(enabled = on, appDeployToken = it.appDeployTokenConfig?.appDeployToken)) }
                },
            )
            val serverToken = data.def.appDeployTokenConfig?.appDeployToken
            if (data.def.appDeployTokenConfig?.enabled == true && !serverToken.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(serverToken, style = MonoStyle, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { copy("App token", serverToken) }) { Icon(Icons.Outlined.ContentCopy, "Copy token") }
                }
                Text(
                    "caprover deploy --appToken <token> --appName ${data.def.appName}",
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if ((token?.enabled == true) != (data.def.appDeployTokenConfig?.enabled == true)) {
                Button(onClick = vm::save, enabled = !vm.busy, modifier = Modifier.padding(top = 8.dp)) { Text("Save & restart") }
            }
        }

        SectionCard("Version history", icon = Icons.Outlined.History, subtitle = "${data.def.versions.size} versions") {
            val versions = data.def.versions.sortedByDescending { it.version }
            if (versions.isEmpty()) {
                Text("Nothing has been deployed yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            versions.forEachIndexed { i, version ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val isCurrent = version.version == data.def.deployedVersion
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("v${version.version}", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            when {
                                isCurrent -> StatusPill("Live", ext.running)
                                version.deployedImageName == null && i == 0 && building -> StatusPill("Building", ext.warning)
                                version.deployedImageName == null -> StatusPill("Failed", MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            listOfNotNull(
                                Format.relative(version.timeStamp),
                                version.gitHash?.takeIf { it.isNotBlank() }?.take(7)?.let { "commit $it" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        version.deployedImageName?.let {
                            Text(it, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    if (!isCurrent && version.deployedImageName != null) {
                        TextButton(onClick = { rollbackTo = version }, enabled = !vm.busy && !building) { Text("Roll back") }
                    }
                }
            }
        }
    }

    rollbackTo?.let { version ->
        ConfirmDialog(
            title = "Roll back to v${version.version}?",
            text = "CapRover re-deploys the image from ${Format.relative(version.timeStamp)} as a new version. " +
                "Environment variables and settings stay as they are now.",
            confirmLabel = "Roll back",
            onConfirm = { vm.rollback(version) },
            onDismiss = { rollbackTo = null },
        )
    }
    pendingUpload?.let { uri ->
        ConfirmDialog(
            title = "Upload and deploy?",
            text = "The selected archive is uploaded to CapRover and built as a new version of ${data.def.appName}.",
            confirmLabel = "Upload",
            onConfirm = { vm.uploadTarball(context.contentResolver, uri) },
            onDismiss = { pendingUpload = null },
        )
    }
}

@Composable
private fun ImageDeploy(busy: Boolean, onDeploy: (String) -> Unit) {
    var image by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = image,
        onValueChange = { image = it.trim() },
        label = { Text("Image") },
        placeholder = { Text("nginx:1.27-alpine") },
        supportingText = { Text("Any image from Docker Hub or a registry CapRover can access") },
        singleLine = true,
        textStyle = MonoStyle,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onDeploy(image) }, enabled = !busy && image.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Outlined.RocketLaunch, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Deploy image")
    }
}

@Composable
private fun CodeDeploy(label: String, placeholder: String, busy: Boolean, onDeploy: (String) -> Unit) {
    var text by rememberSaveable(label) { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = { Text(placeholder, style = MonoStyle) },
        textStyle = MonoStyle,
        minLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onDeploy(text) }, enabled = !busy && text.isNotBlank(), modifier = Modifier.padding(top = 8.dp)) {
        Icon(Icons.Outlined.RocketLaunch, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Build & deploy")
    }
}

@Composable
private fun GitDeploy(vm: AppDetailViewModel, data: AppDetailData) {
    val copy = rememberCopy()
    val session by LocalContainer.current.sessionStore.session.collectAsStateWithLifecycle()
    val baseUrl = session?.baseUrl.orEmpty()
    val def = vm.draft ?: data.def
    val repo = def.appPushWebhook?.repoInfo ?: RepoInfo()
    val saved = data.def.appPushWebhook
    var useSsh by rememberSaveable { mutableStateOf(!repo.sshKey.isNullOrBlank()) }
    fun update(r: RepoInfo) = vm.edit { d ->
        val empty = r.repo.isBlank() && r.branch.isBlank() && r.user.isBlank() && r.password.isBlank() && r.sshKey.isNullOrBlank()
        d.copy(appPushWebhook = if (empty) null else (d.appPushWebhook ?: PushWebhook()).copy(repoInfo = r))
    }
    val repoChanged = def.appPushWebhook?.repoInfo != data.def.appPushWebhook?.repoInfo

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "CapRover pulls and builds the branch. Add the webhook URL to GitHub, GitLab or Bitbucket to deploy on every push.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = repo.repo,
            onValueChange = { update(repo.copy(repo = it.trim())) },
            label = { Text("Repository") },
            placeholder = { Text(if (useSsh) "git@github.com:user/repo.git" else "github.com/user/repo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = repo.branch,
            onValueChange = { update(repo.copy(branch = it.trim())) },
            label = { Text("Branch") },
            placeholder = { Text("main") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !useSsh, onClick = { useSsh = false; update(repo.copy(sshKey = null)) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Username & token")
            }
            SegmentedButton(selected = useSsh, onClick = { useSsh = true; update(repo.copy(user = "", password = "")) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("SSH key")
            }
        }
        if (useSsh) {
            OutlinedTextField(
                value = repo.sshKey.orEmpty(),
                onValueChange = { update(repo.copy(sshKey = it)) },
                label = { Text("Private SSH key") },
                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", style = MonoStyle) },
                textStyle = MonoStyle,
                minLines = 4,
                supportingText = { Text("Use a read-only deploy key without a passphrase") },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = repo.user,
                onValueChange = { update(repo.copy(user = it.trim())) },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = repo.password,
                onValueChange = { update(repo.copy(password = it)) },
                label = "Password or access token",
            )
        }
        if (repoChanged) {
            vm.validationError()?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::save, enabled = repoChanged && !vm.busy) {
                Icon(Icons.Outlined.Merge, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save repository")
            }
            OutlinedButton(onClick = vm::triggerBuild, enabled = !vm.busy && !saved?.pushWebhookToken.isNullOrBlank()) {
                Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Build now")
            }
        }
        val token = saved?.pushWebhookToken
        if (!token.isNullOrBlank()) {
            val url = "$baseUrl/api/v2/user/apps/webhooks/triggerbuild?namespace=captain&token=$token"
            Text("Webhook URL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(url, style = MonoStyle, maxLines = 2, modifier = Modifier.weight(1f))
                IconButton(onClick = { copy("Webhook URL", url) }) { Icon(Icons.Outlined.ContentCopy, "Copy webhook URL") }
            }
        }
    }
}
