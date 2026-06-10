package com.profico.minibreeds.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App-wide Material 3 theme wrapping [content] with the warm brand color scheme
 * and custom typography. Set [dynamicColor] to true to prefer the wallpaper-derived
 * Material You scheme on Android 12+.
 */
@Composable
fun MiniBreedsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the warm brand palette is shown instead of the
    // wallpaper-derived scheme; flip to true to prefer Material You.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
