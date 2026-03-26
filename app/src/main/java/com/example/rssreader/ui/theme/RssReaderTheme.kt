package com.example.rssreader.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.example.rssreader.data.settings.ThemeMode

private val LightRssReaderColors = lightColorScheme(
    primary = Color(0xFF1F5F8B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E6F2),
    onPrimaryContainer = Color(0xFF10283B),
    secondary = Color(0xFF5B738A),
    secondaryContainer = Color(0xFFDCE5EC),
    background = Color(0xFFF2F5F8),
    surface = Color(0xFFF9FBFD),
    surfaceVariant = Color(0xFFE3EAF1),
    outline = Color(0xFF9AA8B6)
)

private val DarkRssReaderColors = darkColorScheme(
    primary = Color(0xFF8ABFE6),
    onPrimary = Color(0xFF0B2233),
    primaryContainer = Color(0xFF1D4764),
    onPrimaryContainer = Color(0xFFD6E6F2),
    secondary = Color(0xFFB5C8D8),
    onSecondary = Color(0xFF1B2A36),
    secondaryContainer = Color(0xFF344857),
    onSecondaryContainer = Color(0xFFDCE5EC),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFD0D0D0),
    outline = Color(0xFF5F5F5F)
)

@Composable
fun RssReaderTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (useDarkTheme) DarkRssReaderColors else LightRssReaderColors

    SideEffect {
        val activity = view.context.findActivity() ?: return@SideEffect
        val window = activity.window
        val barColor = colorScheme.background.toArgb()
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !useDarkTheme
            isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


