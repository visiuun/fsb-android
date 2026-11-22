@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.vis.fsb.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.vis.fsb.data.Settings
import dev.vis.fsb.data.ThemeMode

// Fallback palette seeded from the FSB brand accent #1D9BF0 (used when Material You is off/unavailable).
private val LightScheme = lightColorScheme(
    primary = Color(0xFF00629D), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE5FF), onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF526070), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E4F7), onSecondaryContainer = Color(0xFF0E1D2A),
    tertiary = Color(0xFF7E4E9E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4DAFF), onTertiaryContainer = Color(0xFF310A4F),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FF), onBackground = Color(0xFF181C20),
    surface = Color(0xFFF7F9FF), onSurface = Color(0xFF181C20),
    surfaceVariant = Color(0xFFDEE3EB), onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72787E), outlineVariant = Color(0xFFC2C7CE),
    inverseSurface = Color(0xFF2D3135), inverseOnSurface = Color(0xFFEEF1F6), inversePrimary = Color(0xFF99CBFF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEEF3), surfaceContainerHigh = Color(0xFFE5E8EE),
    surfaceContainerHighest = Color(0xFFE0E3E8), surfaceDim = Color(0xFFD7DADF), surfaceBright = Color(0xFFF7F9FF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF99CBFF), onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF1D9BF0), onPrimaryContainer = Color(0xFF00121F),
    secondary = Color(0xFFB9C8DA), onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857), onSecondaryContainer = Color(0xFFD5E4F7),
    tertiary = Color(0xFFE5B5FF), onTertiary = Color(0xFF47216A),
    tertiaryContainer = Color(0xFF643A84), onTertiaryContainer = Color(0xFFF4DAFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101418), onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF101418), onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF42474E), onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9198), outlineVariant = Color(0xFF42474E),
    inverseSurface = Color(0xFFE0E3E8), inverseOnSurface = Color(0xFF2D3135), inversePrimary = Color(0xFF00629D),
    surfaceContainerLowest = Color(0xFF0B0F12), surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024), surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF31353A), surfaceDim = Color(0xFF101418), surfaceBright = Color(0xFF36393E),
)

/** True-black variant for OLED panels: pure black backgrounds, containers pulled toward black. */
private fun ColorScheme.toOled(): ColorScheme {
    fun dim(c: Color, f: Float) = lerp(c, Color.Black, f)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = dim(surfaceContainerLow, 0.65f),
        surfaceContainer = dim(surfaceContainer, 0.55f),
        surfaceContainerHigh = dim(surfaceContainerHigh, 0.45f),
        surfaceContainerHighest = dim(surfaceContainerHighest, 0.4f),
        surfaceBright = dim(surfaceBright, 0.4f),
        surfaceVariant = dim(surfaceVariant, 0.4f),
        secondaryContainer = dim(secondaryContainer, 0.35f),
        tertiaryContainer = dim(tertiaryContainer, 0.35f),
        inverseOnSurface = Color.Black,
    )
}

data class AppLook(val isDark: Boolean, val oled: Boolean)

val LocalAppLook = staticCompositionLocalOf { AppLook(isDark = false, oled = false) }

/** Status colors that stay legible on any scheme. */
object StatusColors {
    val clean = Color(0xFF2FBF71)
    val warn = Color(0xFFF5A524)
    val bad = Color(0xFFF0506E)
    val unknown = Color(0xFF8C95A3)
}

@Composable
fun FsbTheme(settings: Settings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val oled = dark && settings.oled
    if (oled) scheme = scheme.toOled()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalAppLook provides AppLook(dark, oled)) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = Typography(),
            content = content,
        )
    }
}
