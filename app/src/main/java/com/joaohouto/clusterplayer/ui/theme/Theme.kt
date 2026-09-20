package com.joaohouto.clusterplayer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AutomotiveColorScheme = darkColorScheme(
    primary = NeedleRed,
    onPrimary = TextPrimary,
    primaryContainer = NeedleRedDark,
    onPrimaryContainer = TextPrimary,
    secondary = MetallicIntermediate,
    onSecondary = TextPrimary,
    background = DeepMetallicBackground,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = MetallicIntermediate,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceCardBorder
)

@Composable
fun ClusterPlayerTheme(
    accentTheme: ClusterAccent = AccentNeedleRed,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(accentTheme) {
        darkColorScheme(
            primary = accentTheme.primary,
            onPrimary = TextPrimary,
            primaryContainer = accentTheme.dark,
            onPrimaryContainer = TextPrimary,
            secondary = MetallicIntermediate,
            onSecondary = TextPrimary,
            background = DeepMetallicBackground,
            onBackground = TextPrimary,
            surface = SurfaceCard,
            onSurface = TextPrimary,
            surfaceVariant = MetallicIntermediate,
            onSurfaceVariant = TextSecondary,
            outline = SurfaceCardBorder
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DeepMetallicBackground.toArgb()
                window.navigationBarColor = DeepMetallicBackground.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalClusterAccent provides accentTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}