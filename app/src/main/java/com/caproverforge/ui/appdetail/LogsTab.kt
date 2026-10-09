package com.caproverforge.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caproverforge.data.DockerLogParser
import com.caproverforge.ui.components.ErrorState
import com.caproverforge.ui.components.LoadingState
import com.caproverforge.ui.components.PollingEffect
import com.caproverforge.ui.components.LogViewer
import com.caproverforge.ui.components.rememberCopy
import com.caproverforge.ui.components.shareText

@Composable
internal fun LogsTab(vm: AppDetailViewModel) {
    val context = LocalContext.current
    val copy = rememberCopy()
    var live by rememberSaveable { mutableStateOf(true) }
    var timestamps by rememberSaveable { mutableStateOf(false) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf("") }

    PollingEffect(live, active = live, intervalMs = { 3_000 }) { vm.fetchAppLogs() }

    val raw = vm.appLogs
    val lines = remember(raw, timestamps, filter) {
        val text = raw.orEmpty().let { if (timestamps) it else DockerLogParser.stripTimestamps(it) }
        text.lines()
            .dropLastWhile { it.isBlank() }
            .filter { filter.isBlank() || it.contains(filter, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            item { ToggleChip("Live", live) { live = it } }
            item { ToggleChip("Timestamps", timestamps) { timestamps = it } }
            item { ToggleChip("Wrap lines", wrap) { wrap = it } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text("Filter lines") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { copy("Logs", lines.joinToString("\n")) }, enabled = lines.isNotEmpty()) {
                Icon(Icons.Outlined.ContentCopy, "Copy logs")
            }
            IconButton(onClick = { shareText(context, "${vm.appName} logs", lines.joinToString("\n")) }, enabled = lines.isNotEmpty()) {
                Icon(Icons.Outlined.Share, "Share logs")
            }
        }
        when {
            raw == null && vm.appLogsError != null -> ErrorState(vm.appLogsError!!, onRetry = vm::fetchAppLogs)
            raw == null -> LoadingState()
            else -> {
                vm.appLogsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                LogViewer(
                    lines = lines,
                    wrap = wrap,
                    emptyText = if (filter.isBlank()) "The app hasn't written any logs." else "No lines match “$filter”.",
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }
        } else null,
    )
}
