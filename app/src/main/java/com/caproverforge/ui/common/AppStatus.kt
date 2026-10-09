package com.caproverforge.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.caproverforge.data.AppDefinition
import com.caproverforge.ui.theme.LocalExtendedColors

enum class AppStatus(val label: String) {
    Building("Building"),
    Stopped("Stopped"),
    NotDeployed("Not deployed"),
    BuildFailed("Last build failed"),
    Running("Running"),
}

fun AppDefinition.status(): AppStatus {
    val latest = versions.maxByOrNull { it.version }
    return when {
        isAppBuilding -> AppStatus.Building
        instanceCount == 0 -> AppStatus.Stopped
        isPlaceholder && (latest == null || latest.version == deployedVersion) -> AppStatus.NotDeployed
        latest != null && latest.version > deployedVersion && latest.deployedImageName == null -> AppStatus.BuildFailed
        else -> AppStatus.Running
    }
}

@Composable
fun AppStatus.color(): Color {
    val ext = LocalExtendedColors.current
    return when (this) {
        AppStatus.Building -> ext.warning
        AppStatus.Stopped -> MaterialTheme.colorScheme.outline
        AppStatus.NotDeployed -> MaterialTheme.colorScheme.outline
        AppStatus.BuildFailed -> MaterialTheme.colorScheme.error
        AppStatus.Running -> ext.running
    }
}

/** The public URL CapRover serves an app on (the default sub-domain). */
fun AppDefinition.defaultUrl(rootDomain: String): String =
    "${if (hasDefaultSubDomainSsl) "https" else "http"}://$appName.$rootDomain"
