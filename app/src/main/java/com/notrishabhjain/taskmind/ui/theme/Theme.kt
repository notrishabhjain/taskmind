package com.notrishabhjain.taskmind.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = BlueOnPrimaryContainer,
    surfaceDim = BlueSurfaceDim,
    surface = BlueSurface,
    surfaceBright = BlueSurfaceBright,
    surfaceContainer = BlueSurfaceContainer,
    onSurface = BlueOnSurface,
    onSurfaceVariant = BlueOnSurfaceVariant,
    outline = BlueOutline
)

private val DarkColors = darkColorScheme(
    primary = BlueInversePrimary,
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF1F4876),
    onPrimaryContainer = Color(0xFFD6E3FF),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2024),
    onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099)
)

@Composable
fun TaskMindTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
