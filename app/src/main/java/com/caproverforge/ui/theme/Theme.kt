package com.caproverforge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.caproverforge.data.ThemeMode

data class ExtendedColors(val running: Color, val warning: Color)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(StatusColors.RunningLight, StatusColors.WarningLight)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

@Composable
fun CaproverForgeTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BrandDarkColors
        else -> BrandLightColors
    }
    val extended = if (dark) {
        ExtendedColors(StatusColors.RunningDark, StatusColors.WarningDark)
    } else {
        ExtendedColors(StatusColors.RunningLight, StatusColors.WarningLight)
    }
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}
