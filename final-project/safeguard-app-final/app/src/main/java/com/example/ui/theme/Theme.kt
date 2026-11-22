package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = Color(0xFF06142E),
    primaryContainer = Color(0xFF1B3663),
    onPrimaryContainer = Color(0xFFD6E5FF),
    secondary = SafetyAmber,
    onSecondary = Color(0xFF4D2600),
    secondaryContainer = Color(0xFF6B3700),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = TealAccent,
    onTertiary = Color(0xFF003544),
    background = CommandNavyDark,
    onBackground = TextWhite,
    surface = CommandSurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = CommandSurfaceVariantDark,
    onSurfaceVariant = TextMuted,
    outline = CommandBorderDark,
    error = HazardCrimson,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CommandLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = CommandLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2CC),
    onSecondaryContainer = Color(0xFF3B1600),
    tertiary = CommandLightTeal,
    onTertiary = Color.White,
    background = CommandLightBg,
    onBackground = TextDark,
    surface = CommandLightSurface,
    onSurface = TextDark,
    surfaceVariant = CommandLightSurfaceVariant,
    onSurfaceVariant = TextDarkMuted,
    outline = CommandLightBorder,
    error = CommandLightRed,
    onError = Color.White
)

@Composable
fun SafeGuardTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
