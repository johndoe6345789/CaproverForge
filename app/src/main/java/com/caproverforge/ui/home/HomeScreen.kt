package com.caproverforge.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.caproverforge.ui.apps.AppsScreen
import com.caproverforge.ui.dashboard.DashboardScreen
import com.caproverforge.ui.navigation.Navigator
import com.caproverforge.ui.oneclick.OneClickListScreen
import com.caproverforge.ui.server.ServerScreen

private data class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    Tab("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    Tab("One-Click", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    Tab("Server", Icons.Filled.Dns, Icons.Outlined.Dns),
)

@Composable
fun HomeScreen(navigator: Navigator) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(if (selected == index) tab.selected else tab.unselected, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            Crossfade(targetState = selected, label = "tab") { tab ->
                when (tab) {
                    0 -> DashboardScreen(navigator, onShowApps = { selected = 1 })
                    1 -> AppsScreen(navigator)
                    2 -> OneClickListScreen(navigator)
                    else -> ServerScreen(navigator)
                }
            }
        }
    }
}
