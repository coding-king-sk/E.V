package com.ev.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Sirf ek theme — hamesha dark.
 *
 * Pehle Android 12+ pe "dynamic color" on tha, jo phone ke wallpaper se colors
 * uthata hai. Iska matlab tha ki E.V har phone pe alag dikhta — kisi pe neela,
 * kisi pe gulabi. HUD look ke liye wo bilkul theek nahi, isliye hata diya.
 */
private val EvColorScheme = darkColorScheme(
    primary = EvGreen,
    onPrimary = EvBlack,
    primaryContainer = EvSurfaceHigh,
    onPrimaryContainer = EvGreen,
    secondary = EvGreenDim,
    onSecondary = EvBlack,
    secondaryContainer = EvSurfaceHigh,
    onSecondaryContainer = EvGreen,
    tertiary = EvGreenGlow,
    onTertiary = EvBlack,
    tertiaryContainer = EvSurfaceHigh,
    onTertiaryContainer = EvGreen,
    background = EvBlack,
    onBackground = EvTextPrimary,
    surface = EvSurface,
    onSurface = EvTextPrimary,
    surfaceVariant = EvSurfaceHigh,
    onSurfaceVariant = EvTextMuted,
    outline = EvOutline,
    outlineVariant = EvOutline,
    error = EvRed,
    onError = EvBlack,
    errorContainer = EvSurfaceHigh,
    onErrorContainer = EvRed,
    scrim = EvBlack,
)

@Composable
fun EVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvColorScheme,
        typography = Typography,
        content = content,
    )
}
