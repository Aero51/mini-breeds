package com.profico.minibreeds.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Light color scheme — warm amber/brown ("golden retriever") palette with a sage green tertiary. */
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8DEC6),
    onSecondaryContainer = Color(0xFF291806),
    tertiary = Color(0xFF58633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE8B4),
    onTertiaryContainer = Color(0xFF161E01),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF221A11),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF221A11),
    surfaceVariant = Color(0xFFF2DFD1),
    onSurfaceVariant = Color(0xFF51453A),
    outline = Color(0xFF837468),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFDF1E7),
    surfaceContainer = Color(0xFFF7EBDF),
    surfaceContainerHigh = Color(0xFFF1E5D8),
    surfaceContainerHighest = Color(0xFFEBDFD2),
)

/** Dark variant of the warm amber/brown palette. */
internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB870),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF59422C),
    onSecondaryContainer = Color(0xFFF8DEC6),
    tertiary = Color(0xFFC0CC9A),
    onTertiary = Color(0xFF2A3410),
    tertiaryContainer = Color(0xFF404B24),
    onTertiaryContainer = Color(0xFFDCE8B4),
    background = Color(0xFF19120C),
    onBackground = Color(0xFFEFE0D5),
    surface = Color(0xFF19120C),
    onSurface = Color(0xFFEFE0D5),
    surfaceVariant = Color(0xFF51453A),
    onSurfaceVariant = Color(0xFFD5C3B5),
    outline = Color(0xFF9D8E81),
    surfaceContainerLowest = Color(0xFF140D08),
    surfaceContainerLow = Color(0xFF211A13),
    surfaceContainer = Color(0xFF251E17),
    surfaceContainerHigh = Color(0xFF302921),
    surfaceContainerHighest = Color(0xFF3B332B),
)
