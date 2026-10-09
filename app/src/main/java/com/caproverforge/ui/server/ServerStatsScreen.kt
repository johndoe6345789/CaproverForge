package com.caproverforge.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.caproverforge.data.CapRoverException
import com.caproverforge.data.CapRoverRepository
import com.caproverforge.data.Format
import com.caproverforge.data.ServerStats
import com.caproverforge.data.StatsRange
import com.caproverforge.data.serverStats
import com.caproverforge.ui.common.Load
import com.caproverforge.ui.common.LoadingViewModel
import com.caproverforge.ui.components.BackTopBar
import com.caproverforge.ui.components.ChartLine
import com.caproverforge.ui.components.CollectMessages
import com.caproverforge.ui.components.EmptyState
import com.caproverforge.ui.components.LoadContent
import com.caproverforge.ui.components.LocalSnackbar
import com.caproverforge.ui.components.MetricChartCard
import com.caproverforge.ui.components.PollingEffect
import com.caproverforge.ui.components.SectionCard
import com.caproverforge.ui.components.UsageMeter
import com.caproverforge.ui.components.UsageWarning
import com.caproverforge.ui.components.chartSeriesColors
import com.caproverforge.ui.components.formatKbps
import com.caproverforge.ui.components.formatLoad
import com.caproverforge.ui.components.formatPercent
import com.caproverforge.ui.navigation.containerViewModel

class ServerStatsViewModel(private val repo: CapRoverRepository) : LoadingViewModel<ServerStats>() {
    var range by mutableStateOf(StatsRange.FiveMinutes)
        private set

    init { refresh() }

    override suspend fun load() = repo.serverStats(range)

    fun selectRange(r: StatsRange) {
        if (r == range) return
        range = r
        refresh()
    }
}

@Composable
fun ServerStatsScreen(onBack: () -> Unit, onOpenMonitoring: () -> Unit, onSignInAgain: () -> Unit) {
    val vm = containerViewModel { ServerStatsViewModel(it.repository) }
    CollectMessages(vm.messages)
    PollingEffect(vm.range, immediate = false, intervalMs = { if (vm.range == StatsRange.FiveMinutes) 5_000 else 30_000 }) {
        vm.quietReload()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
        topBar = { BackTopBar("Server stats", onBack, subtitle = vm.data?.hostname?.let { "$it · live from NetData" } ?: "Live from NetData") },
    ) { padding ->
        val failed = vm.state as? Load.Failed
        when (failed?.status) {
            CapRoverException.MONITOR_OFF -> EmptyState(
                icon = Icons.Outlined.MonitorHeart,
                title = "Monitoring is off",
                body = "CapRover collects CPU, memory, network and disk stats with NetData. Turn it on to see them here.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onOpenMonitoring) { Text("Set up monitoring") } },
            )
            CapRoverException.MONITOR_SIGN_IN -> EmptyState(
                icon = Icons.AutoMirrored.Outlined.Login,
                title = "Sign in again for stats",
                body = "Server stats need a monitoring cookie that CapRover only hands out at sign-in. Sign out and back in once to enable them.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onSignInAgain) { Text("Sign out and sign in") } },
            )
            else -> LoadContent(vm.state, vm.refreshing, { vm.refresh(true) }, Modifier.padding(padding)) { stats ->
                StatsContent(stats, vm.range, vm::selectRange)
            }
        }
    }
}

private const val GIB = 1024.0 * 1024 * 1024

@Composable
private fun StatsContent(stats: ServerStats, range: StatsRange, onRange: (StatsRange) -> Unit) {
    val colors = chartSeriesColors()
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsRange.entries.forEach { r ->
                FilterChip(selected = r == range, onClick = { onRange(r) }, label = { Text(r.label) })
            }
        }
        val facts = listOfNotNull(
            stats.os,
            stats.cores?.let { "$it cores" },
            stats.memoryTotalMiB?.let { Format.bytes((it * 1024 * 1024).toLong()) + " RAM" },
        )
        if (facts.isNotEmpty()) {
            Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        MetricChartCard(
            title = "CPU",
            lines = listOf(ChartLine("CPU", stats.cpuPercent, primary)),
            formatValue = ::formatPercent,
            formatAxis = { "${it.toInt()}%" },
            yMax = 100.0,
            subtitle = "All cores",
        )
        MetricChartCard(
            title = "Memory",
            lines = listOf(ChartLine("Memory", stats.memoryPercent, primary)),
            formatValue = ::formatPercent,
            formatAxis = { "${it.toInt()}%" },
            yMax = 100.0,
            subtitle = if (stats.memoryUsedMiB != null && stats.memoryTotalMiB != null) {
                "${Format.bytes((stats.memoryUsedMiB * 1048576).toLong())} of ${Format.bytes((stats.memoryTotalMiB * 1048576).toLong())} used"
            } else null,
        )
        stats.disk?.let { disk ->
            SectionCard("Disk", icon = Icons.Outlined.Storage, trailing = {
                Text(formatPercent(disk.fraction * 100), style = MaterialTheme.typography.headlineSmall)
            }) {
                UsageMeter(disk.fraction)
                Text(
                    "${Format.bytes((disk.usedGiB * GIB).toLong())} of ${Format.bytes((disk.totalGiB * GIB).toLong())} used on /",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                UsageWarning(disk.fraction, "The disk")
            }
        }
        if (!stats.netInKbps.isEmpty) {
            MetricChartCard(
                title = "Network",
                lines = listOf(
                    ChartLine("In", stats.netInKbps, colors[0]),
                    ChartLine("Out", stats.netOutKbps, colors[1]),
                ),
                formatValue = ::formatKbps,
            )
        }
        if (!stats.load1.isEmpty) {
            MetricChartCard(
                title = "Load average",
                lines = listOf(ChartLine("1 min", stats.load1, primary)),
                formatValue = ::formatLoad,
                subtitle = listOfNotNull(
                    stats.load5?.let { "5 min ${formatLoad(it)}" },
                    stats.load15?.let { "15 min ${formatLoad(it)}" },
                    stats.cores?.let { "$it cores" },
                ).joinToString(" · "),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Touch and drag a chart to read past values." + (stats.netDataVersion?.let { " NetData $it." } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
