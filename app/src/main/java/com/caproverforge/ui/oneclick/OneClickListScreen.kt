package com.caproverforge.ui.oneclick

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.OneClickAppSummary
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.EmptyState
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.navigation.Navigator
import com.caproverforge.ui.navigation.OneClickReposRoute
import com.caproverforge.ui.navigation.containerViewModel

class OneClickListViewModel(private val repo: CapRoverRepository) : LoadingViewModel<List<OneClickAppSummary>>() {
    var query by mutableStateOf("")

    init { refresh() }

    override suspend fun load(): List<OneClickAppSummary> =
        repo.oneClickApps().sortedWith(compareByDescending<OneClickAppSummary> { it.isOfficial }.thenBy { it.displayName.lowercase() })

    fun filtered(all: List<OneClickAppSummary>): List<OneClickAppSummary> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all
            .filter { it.displayName.contains(q, true) || it.name.contains(q, true) || it.description.contains(q, true) }
            .sortedBy { if (it.displayName.startsWith(q, true) || it.name.startsWith(q, true)) 0 else 1 }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickListScreen(navigator: Navigator) {
    val vm = containerViewModel { OneClickListViewModel(it.repository) }
    CollectMessages(vm.messages)

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = {
            TopAppBar(
                title = { Text("One-Click Apps") },
                actions = {
                    IconButton(onClick = { navigator.open(OneClickReposRoute) }) {
                        Icon(Icons.Outlined.Source, "Template repositories")
                    }
                },
            )
        },
    ) { padding ->
        LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }, Modifier.padding(padding)) { all ->
            val apps = vm.filtered(all)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        placeholder = { Text("Search ${all.size} apps") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = if (vm.query.isNotEmpty()) {
                            { IconButton(onClick = { vm.query = "" }) { Icon(Icons.Outlined.Close, "Clear search") } }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (apps.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(Icons.Outlined.SearchOff, "No templates found", "Try another search, or add a template repository.")
                    }
                }
                items(apps, key = { it.baseUrl + "/" + it.name }) { app ->
                    OneClickCard(app) { navigator.openOneClick(app.name, app.baseUrl, app.displayName) }
                }
            }
        }
    }
}

@Composable
private fun OneClickCard(app: OneClickAppSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().height(176.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo(app, Modifier.size(44.dp))
                Spacer(Modifier.weight(1f))
                if (app.isOfficial) {
                    Icon(Icons.Outlined.Verified, "Official", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(app.displayName.ifBlank { app.name }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                app.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AppLogo(app: OneClickAppSummary?, modifier: Modifier = Modifier, fallbackName: String = "") {
    val name = app?.displayName?.ifBlank { app.name } ?: fallbackName
    val fallback = @Composable {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (app?.logoUrl.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = app.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                error = { fallback() },
                loading = { Spacer(Modifier.width(1.dp)) },
            )
        }
    }
}
