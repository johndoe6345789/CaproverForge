package com.caproverforge.ui.oneclick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.BusyBar
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.ConfirmDialog
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.TextInputDialog
import com.caproverforge.ui.navigation.containerViewModel

class OneClickReposViewModel(private val repo: CapRoverRepository) : LoadingViewModel<List<String>>() {
    init { refresh() }
    override suspend fun load() = repo.oneClickRepositories()
    fun add(url: String) = action(success = "Repository added") { repo.addOneClickRepository(url) }
    fun delete(url: String) = action(success = "Repository removed") { repo.deleteOneClickRepository(url) }
}

@Composable
fun OneClickReposScreen(onBack: () -> Unit) {
    val vm = containerViewModel { OneClickReposViewModel(it.repository) }
    CollectMessages(vm.messages)
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = { BackTopBar("Template repositories", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            BusyBar(vm.busy)
            LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }) { urls ->
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            "One-click templates come from the official repository plus any third-party repositories you add here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (urls.isEmpty()) {
                        item { Text("No third-party repositories.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(urls) { url ->
                        ListItem(
                            headlineContent = { Text(url) },
                            leadingContent = { Icon(Icons.Outlined.Source, null) },
                            trailingContent = {
                                IconButton(onClick = { deleting = url }) { Icon(Icons.Outlined.Delete, "Remove $url") }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.clip(MaterialTheme.shapes.large),
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        TextInputDialog(
            title = "Add repository",
            label = "Repository URL",
            placeholder = "https://example.com/one-click-apps",
            supportingText = "Only add repositories you trust; templates run with full access to your server.",
            keyboardType = KeyboardType.Uri,
            confirmLabel = "Add",
            validate = { if (it.startsWith("https://") || it.startsWith("http://")) null else "Must start with https://" },
            onConfirm = vm::add,
            onDismiss = { adding = false },
        )
    }
    deleting?.let { url ->
        ConfirmDialog(
            title = "Remove repository?",
            text = url,
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = { vm.delete(url) },
            onDismiss = { deleting = null },
        )
    }
}
