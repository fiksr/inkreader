package com.example.inkreader.theme

import androidx.compose.ui.graphics.Color

// E-Ink Paper Palette
val InkPaperBg = Color(0xFFF3F0E8)
val InkPaperText = Color(0xFF121212)
val InkPaperBorder = Color(0xFF222222)
val InkPaperMuted = Color(0xFF6B665E)
val InkPaperCard = Color(0xFFE8E4DA)

// Pure E-Ink White
val InkWhiteBg = Color(0xFFFFFFFF)
val InkWhiteText = Color(0xFF000000)
val InkWhiteBorder = Color(0xFF000000)
val InkWhiteMuted = Color(0xFF555555)
val InkWhiteCard = Color(0xFFF0F0F0)

// Pure OLED Black
val InkOledBg = Color(0xFF000000)
val InkOledText = Color(0xFFEBEBEB)
val InkOledBorder = Color(0xFF444444)
val InkOledMuted = Color(0xFF888888)
val InkOledCard = Color(0xFF141414)

// Warm Sepia
val InkSepiaBg = Color(0xFFEFE7D8)
val InkSepiaText = Color(0xFF2E261F)
val InkSepiaBorder = Color(0xFF3F352B)
val InkSepiaMuted = Color(0xFF7A6E5F)
val InkSepiaCard = Color(0xFFE3D9C6)

enum class EInkThemeMode(val displayName: String) {
    PAPER("Paper"),
    WHITE("White"),
    OLED_DARK("OLED Dark"),
    SEPIA("Sepia")
}

data class EInkColors(
    val background: Color,
    val text: Color,
    val border: Color,
    val muted: Color,
    val card: Color,
    val isDark: Boolean = false
) {
    val textPrimary: Color get() = text
    val textSecondary: Color get() = muted
    val divider: Color get() = border
    val surface: Color get() = card
}

fun getEInkColors(mode: EInkThemeMode): EInkColors = when (mode) {
    EInkThemeMode.PAPER -> EInkColors(InkPaperBg, InkPaperText, InkPaperBorder, InkPaperMuted, InkPaperCard, false)
    EInkThemeMode.WHITE -> EInkColors(InkWhiteBg, InkWhiteText, InkWhiteBorder, InkWhiteMuted, InkWhiteCard, false)
    EInkThemeMode.OLED_DARK -> EInkColors(InkOledBg, InkOledText, InkOledBorder, InkOledMuted, InkOledCard, true)
    EInkThemeMode.SEPIA -> EInkColors(InkSepiaBg, InkSepiaText, InkSepiaBorder, InkSepiaMuted, InkSepiaCard, false)
}
