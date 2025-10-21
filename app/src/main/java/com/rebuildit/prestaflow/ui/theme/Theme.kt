package com.rebuildit.prestaflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.rebuildit.prestaflow.domain.theme.DarkThemeConfig
import com.rebuildit.prestaflow.domain.theme.ThemeSettings

@Composable
fun PrestaFlowTheme(
    settings: ThemeSettings = ThemeSettings(),
    content: @Composable () -> Unit
) {
    val darkTheme = resolveDarkTheme(settings.darkThemeConfig)
    val dynamicColorEnabled = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColorEnabled -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> settings.skin.colorScheme(darkTheme)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
@ReadOnlyComposable
private fun resolveDarkTheme(config: DarkThemeConfig): Boolean = when (config) {
    DarkThemeConfig.DARK -> true
    DarkThemeConfig.LIGHT -> false
    DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
}
