package com.caproverforge.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(prefs.getString("theme", null)!!) }
                .getOrDefault(ThemeMode.System),
            dynamicColor = prefs.getBoolean("dynamic", false),
        )
    )
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString("theme", mode.name) }
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit { putBoolean("dynamic", enabled) }
        _settings.value = _settings.value.copy(dynamicColor = enabled)
    }
}
