package com.caproverforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caproverforge.ui.navigation.AppRoot
import com.caproverforge.ui.theme.CaproverForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CaproverForgeApp).container
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle()
            CaproverForgeTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                AppRoot(container)
            }
        }
    }
}
