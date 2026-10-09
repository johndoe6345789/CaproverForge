package com.caproverforge.ui.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caproverforge.data.App
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.CaptainInfo
import com.caproverforge.data.Format
import com.caproverforge.data.LoadBalancerInfo
import com.caproverforge.data.NodeInfo
import com.caproverforge.data.ServerAddress
import com.caproverforge.data.VersionInfo
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.PollingEffect
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.StatusPill
import com.caproverforge.ui.components.openUrl
import com.caproverforge.ui.navigation.LocalContainer
import com.caproverforge.ui.navigation.Navigator
import com.caproverforge.ui.navigation.UpdateRoute
import com.caproverforge.ui.navigation.containerViewModel
import com.caproverforge.ui.theme.LocalExtendedColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class DashboardData(
    val info: CaptainInfo,
    val version: VersionInfo?,
    val loadBalancer: LoadBalancerInfo?,
    val nodes: List<NodeInfo>,
    val apps: List<App>,
    val rootDomain: String,
)

class DashboardViewModel(private val repo: CapRoverRepository) : LoadingViewModel<DashboardData>() {
    init { refresh() }

    override suspend fun load(): DashboardData = coroutineScope {
        val info = async { repo.captainInfo() }
        val version = async { runCatching { repo.versionInfo() }.getOrNull() }
        val lb = async { runCatching { repo.loadBalancerInfo() }.getOrNull() }
        val nodes = async { runCatching { repo.nodes() }.getOrDefault(emptyList()) }
        val apps = async { repo.apps() }
        val appsData = apps.await()
        DashboardData(info.await(), version.await(), lb.await(), nodes.await(), appsData.apps, appsData.rootDomain)
    }

    fun poll() = quietReload()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navigator: Navigator, onShowApps: () -> Unit) {
    val vm = containerViewModel { DashboardViewModel(it.repository) }
    CollectMessages(vm.messages)
    val context = LocalContext.current
    val sessionState by LocalContainer.current.sessionStore.session.collectAsStateWithLifecycle()
    val session = sessionState
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) { vm.quietReload() }
    PollingEffect(immediate = false, intervalMs = { 15_000 }) { vm.poll() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard")
                        if (session != null) {
                            Text(
                                ServerAddress.displayHost(session.baseUrl),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = { openUrl(context, session.baseUrl) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open web dashboard")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }, Modifier.padding(padding)) { data ->
            DashboardContent(
                data = data,
                onUpdate = { navigator.open(UpdateRoute) },
                onOpenApp = navigator::openApp,
                onShowApps = onShowApps,
                onOpenUrl = { openUrl(context, it) },
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    onUpdate: () -> Unit,
    onOpenApp: (String) -> Unit,
    onShowApps: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val ext = LocalExtendedColors.current
    val running = data.apps.sumOf { it.def.instanceCount }
    val building = data.apps.filter { it.def.isAppBuilding }
    val recent = data.apps
        .mapNotNull { app -> app.def.versions.maxByOrNull { it.version }?.let { app to it } }
        .sortedByDescending { it.second.timeStamp }
        .take(5)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val version = data.version
        if (version != null && version.canUpdate) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NewReleases, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "CapRover ${version.latestVersion} is available",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "You're running ${version.currentVersion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        FilledTonalButton(onClick = onUpdate) { Text("Details") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Apps", "${data.apps.size}", Icons.Outlined.Apps, Modifier.weight(1f), onShowApps)
                StatTile("Instances", "$running", Icons.Outlined.Layers, Modifier.weight(1f), onShowApps)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Nodes", "${data.nodes.size}", Icons.Outlined.Hub, Modifier.weight(1f))
                StatTile(
                    "Connections",
                    data.loadBalancer?.activeConnections?.let { Format.number(it) } ?: "–",
                    Icons.Outlined.SwapVert,
                    Modifier.weight(1f),
                )
            }
        }

        if (building.isNotEmpty()) {
            item {
                SectionCard("Building now", icon = Icons.Outlined.RocketLaunch) {
                    building.forEach { app ->
                        AppLine(app.def.appName, "Build in progress", onClick = { onOpenApp(app.def.appName) }) {
                            StatusPill("Building", ext.warning)
                        }
                    }
                }
            }
        }

        item {
            val scheme = if (data.info.hasRootSsl) "https" else "http"
            val dashboardUrl = "$scheme://${data.info.captainSubDomain}.${data.info.rootDomain}"
            SectionCard("Server", icon = Icons.Outlined.Public) {
                KeyValue("Root domain", data.info.rootDomain.ifBlank { "Not set" })
                KeyValue("CapRover version", data.version?.currentVersion ?: "Unknown")
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTPS", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        data.info.hasRootSsl && data.info.forceSsl -> StatusPill("Enabled · forced", ext.running)
                        data.info.hasRootSsl -> StatusPill("Enabled", ext.running)
                        else -> StatusPill("Not enabled", ext.warning)
                    }
                }
                if (data.info.rootDomain.isNotBlank()) {
                    TextButton(onClick = { onOpenUrl(dashboardUrl) }, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Outlined.Language, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dashboardUrl.substringAfter("://"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        data.loadBalancer?.let { lb ->
            item {
                SectionCard("Load balancer", subtitle = "NGINX live connections", icon = Icons.Outlined.Lan) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        MiniStat("Reading", lb.reading)
                        MiniStat("Writing", lb.writing)
                        MiniStat("Waiting", lb.waiting)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    KeyValue("Requests handled", Format.number(lb.total))
                    KeyValue("Connections accepted", Format.number(lb.accepted))
                }
            }
        }

        if (data.nodes.isNotEmpty()) {
            item {
                SectionCard("Nodes", icon = Icons.Outlined.Memory) {
                    data.nodes.forEachIndexed { i, node ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(node.hostname, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull(
                                        if (node.isLeader) "Leader" else node.type.replaceFirstChar { it.uppercase() },
                                        Format.cpus(node.nanoCpu),
                                        Format.bytes(node.memoryBytes),
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            val ready = node.status.equals("ready", true)
                            StatusPill(node.status.ifBlank { node.state }.replaceFirstChar { it.uppercase() }, if (ready) ext.running else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item {
                SectionCard("Recent deployments", icon = Icons.Outlined.RocketLaunch) {
                    recent.forEach { (app, version) ->
                        AppLine(
                            app.def.appName,
                            "v${version.version} · ${Format.relative(version.timeStamp)}",
                            onClick = { onOpenApp(app.def.appName) },
                        ) {
                            if (version.deployedImageName == null && app.def.isAppBuilding) {
                                StatusPill("Building", ext.warning)
                            } else if (version.deployedImageName == null) {
                                StatusPill("Failed", MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(16.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Format.number(value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(key, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppLine(title: String, subtitle: String, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}
