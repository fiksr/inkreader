package com.fiksr.inkreader.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalEInkColors = staticCompositionLocalOf {
    getEInkColors(EInkThemeMode.PAPER)
}

@Composable
fun InkReaderTheme(
    mode: EInkThemeMode = EInkThemeMode.PAPER,
    content: @Composable () -> Unit
) {
    val eInkColors = getEInkColors(mode)
    val colorScheme = if (eInkColors.isDark) {
        darkColorScheme(
            primary = eInkColors.text,
            onPrimary = eInkColors.background,
            background = eInkColors.background,
            onBackground = eInkColors.text,
            surface = eInkColors.card,
            onSurface = eInkColors.text,
            outline = eInkColors.border
        )
    } else {
        lightColorScheme(
            primary = eInkColors.text,
            onPrimary = eInkColors.background,
            background = eInkColors.background,
            onBackground = eInkColors.text,
            surface = eInkColors.card,
            onSurface = eInkColors.text,
            outline = eInkColors.border
        )
    }

    CompositionLocalProvider(LocalEInkColors provides eInkColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
