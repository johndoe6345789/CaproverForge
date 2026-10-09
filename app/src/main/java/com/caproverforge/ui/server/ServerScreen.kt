package com.caproverforge.ui.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caproverforge.BuildConfig
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.ServerAddress
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.common.userMessage
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.BusyBar
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.SectionLabel
import com.caproverforge.ui.components.openUrl
import com.caproverforge.ui.navigation.AppearanceRoute
import com.caproverforge.ui.navigation.DiskCleanupRoute
import com.caproverforge.ui.navigation.DomainRoute
import com.caproverforge.ui.navigation.LocalContainer
import com.caproverforge.ui.navigation.MonitoringRoute
import com.caproverforge.ui.navigation.Navigator
import com.caproverforge.ui.navigation.NginxRoute
import com.caproverforge.ui.navigation.NodesRoute
import com.caproverforge.ui.navigation.PasswordRoute
import com.caproverforge.ui.navigation.ProjectsRoute
import com.caproverforge.ui.navigation.RegistriesRoute
import com.caproverforge.ui.navigation.UpdateRoute
import com.caproverforge.ui.navigation.containerViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ServerMenuViewModel(private val repo: CapRoverRepository) : ViewModel() {
    var creatingBackup by mutableStateOf(false)
        private set
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    fun createBackup(onReady: (String) -> Unit) {
        if (creatingBackup) return
        viewModelScope.launch {
            creatingBackup = true
            try {
                onReady(repo.createBackupUrl())
                messages.tryEmit("Backup is ready. Download started in your browser.")
            } catch (e: Exception) {
                messages.tryEmit(e.userMessage())
            } finally {
                creatingBackup = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(navigator: Navigator) {
    val vm = containerViewModel { ServerMenuViewModel(it.repository) }
    CollectMessages(vm.messages)
    val context = LocalContext.current
    val session by LocalContainer.current.sessionStore.session.collectAsStateWithLifecycle()
    var confirmBackup by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("Server")
                    session?.let {
                        Text(
                            ServerAddress.displayHost(it.baseUrl),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BusyBar(vm.creatingBackup)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { SectionLabel("Infrastructure") }
                item {
                    MenuGroup(
                        MenuEntry("Cluster nodes", "Servers in your Docker swarm", Icons.Outlined.Hub) { navigator.open(NodesRoute) },
                        MenuEntry("Domain & HTTPS", "Root domain, certificates, force HTTPS", Icons.Outlined.Language) { navigator.open(DomainRoute) },
                        MenuEntry("NGINX", "Base and dashboard load-balancer config", Icons.Outlined.Code) { navigator.open(NginxRoute) },
                    )
                }
                item { SectionLabel("Docker") }
                item {
                    MenuGroup(
                        MenuEntry("Registries", "Where images are pushed and pulled", Icons.Outlined.Inventory) { navigator.open(RegistriesRoute) },
                        MenuEntry("Disk cleanup", "Remove unused images and free space", Icons.Outlined.CleaningServices) { navigator.open(DiskCleanupRoute) },
                    )
                }
                item { SectionLabel("Organisation") }
                item {
                    MenuGroup(
                        MenuEntry("Projects", "Group related apps", Icons.Outlined.Folder) { navigator.open(ProjectsRoute) },
                        MenuEntry("Monitoring", "NetData server metrics", Icons.Outlined.MonitorHeart) { navigator.open(MonitoringRoute) },
                    )
                }
                item { SectionLabel("Maintenance") }
                item {
                    MenuGroup(
                        MenuEntry("CapRover version", "Check for and install updates", Icons.Outlined.SystemUpdateAlt) { navigator.open(UpdateRoute) },
                        MenuEntry("Create backup", "Download CapRover's configuration", Icons.Outlined.Archive) { confirmBackup = true },
                        MenuEntry("Change password", "Dashboard password", Icons.Outlined.Password) { navigator.open(PasswordRoute) },
                    )
                }
                item { SectionLabel("This app") }
                item {
                    MenuGroup(
                        MenuEntry("Appearance", "Theme and colours", Icons.Outlined.Palette) { navigator.open(AppearanceRoute) },
                        MenuEntry("Sign out", session?.let { "Signed in to ${ServerAddress.displayHost(it.baseUrl)}" }, Icons.AutoMirrored.Outlined.Logout, chevron = false) {
                            confirmSignOut = true
                        },
                    )
                }
                item {
                    Text(
                        "CaproverForge ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (confirmBackup) {
        ConfirmDialog(
            title = "Create a backup?",
            text = "CapRover packages its configuration (apps, settings and certificates, not app data volumes) into a .tar file that you download through your browser.",
            confirmLabel = "Create backup",
            icon = Icons.Outlined.Archive,
            onConfirm = { vm.createBackup { url -> openUrl(context, url) } },
            onDismiss = { confirmBackup = false },
        )
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            text = "You'll need your CapRover password to sign in again.",
            confirmLabel = "Sign out",
            onConfirm = navigator::signOut,
            onDismiss = { confirmSignOut = false },
        )
    }
}

class MenuEntry(
    val title: String,
    val subtitle: String?,
    val icon: ImageVector,
    val chevron: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun MenuGroup(vararg entries: MenuEntry) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        entries.forEachIndexed { i, entry ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ListItem(
                headlineContent = { Text(entry.title) },
                supportingContent = entry.subtitle?.let { { Text(it) } },
                leadingContent = { Icon(entry.icon, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = if (entry.chevron) {
                    { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
                } else null,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clickable(onClick = entry.onClick),
            )
        }
    }
}

/** Scaffold for the server sub-pages: back bar, busy indicator, pull-to-refresh content. */
@Composable
fun <T> ServerPage(
    title: String,
    vm: LoadingViewModel<T>,
    onBack: () -> Unit,
    subtitle: String? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable ColumnScope.(T) -> Unit,
) {
    CollectMessages(vm.messages)
    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = { BackTopBar(title, onBack, subtitle = subtitle) },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BusyBar(vm.busy)
            LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }) { data ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { content(data) }
            }
        }
    }
}
