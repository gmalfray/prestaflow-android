package com.rebuildit.prestaflow.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = RoyalVioletPrimary,
    onPrimary = RoyalVioletOnPrimary,
    primaryContainer = RoyalVioletPrimaryContainer,
    secondary = RoyalVioletSecondary,
    onSecondary = RoyalVioletOnSecondary,
    error = RoyalVioletError,
    background = RoyalVioletBackground,
    onBackground = RoyalVioletOnBackground
)

private val DarkColors = darkColorScheme(
    primary = RoyalVioletPrimaryDark,
    onPrimary = RoyalVioletOnPrimaryDark,
    primaryContainer = RoyalVioletPrimaryContainerDark,
    secondary = RoyalVioletSecondaryDark,
    onSecondary = RoyalVioletOnSecondaryDark,
    error = RoyalVioletErrorDark,
    background = RoyalVioletBackgroundDark,
    onBackground = RoyalVioletOnBackgroundDark
)

@Composable
fun PrestaFlowTheme(
    useDynamicColor: Boolean = true,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
