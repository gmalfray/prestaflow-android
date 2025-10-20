package com.rebuildit.prestaflow.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.theme.PrestaFlowSkin

internal data class SkinPalette(
    val light: ColorScheme,
    val dark: ColorScheme
)

private val palettes: Map<PrestaFlowSkin, SkinPalette> = mapOf(
    PrestaFlowSkin.ROYAL to SkinPalette(
        light = lightColorScheme(
            primary = RoyalLightPrimary,
            onPrimary = RoyalLightOnPrimary,
            primaryContainer = RoyalLightPrimaryContainer,
            onPrimaryContainer = RoyalLightOnPrimaryContainer,
            secondary = RoyalLightSecondary,
            onSecondary = RoyalLightOnSecondary,
            secondaryContainer = RoyalLightSecondaryContainer,
            onSecondaryContainer = RoyalLightOnSecondaryContainer,
            tertiary = RoyalLightTertiary,
            onTertiary = RoyalLightOnTertiary,
            tertiaryContainer = RoyalLightTertiaryContainer,
            onTertiaryContainer = RoyalLightOnTertiaryContainer,
            error = RoyalLightError,
            onError = RoyalLightOnError,
            errorContainer = RoyalLightErrorContainer,
            onErrorContainer = RoyalLightOnErrorContainer,
            background = RoyalLightBackground,
            onBackground = RoyalLightOnBackground,
            surface = RoyalLightSurface,
            onSurface = RoyalLightOnSurface,
            surfaceVariant = RoyalLightSurfaceVariant,
            onSurfaceVariant = RoyalLightOnSurfaceVariant,
            outline = RoyalLightOutline
        ),
        dark = darkColorScheme(
            primary = RoyalDarkPrimary,
            onPrimary = RoyalDarkOnPrimary,
            primaryContainer = RoyalDarkPrimaryContainer,
            onPrimaryContainer = RoyalDarkOnPrimaryContainer,
            secondary = RoyalDarkSecondary,
            onSecondary = RoyalDarkOnSecondary,
            secondaryContainer = RoyalDarkSecondaryContainer,
            onSecondaryContainer = RoyalDarkOnSecondaryContainer,
            tertiary = RoyalDarkTertiary,
            onTertiary = RoyalDarkOnTertiary,
            tertiaryContainer = RoyalDarkTertiaryContainer,
            onTertiaryContainer = RoyalDarkOnTertiaryContainer,
            error = RoyalDarkError,
            onError = RoyalDarkOnError,
            errorContainer = RoyalDarkErrorContainer,
            onErrorContainer = RoyalDarkOnErrorContainer,
            background = RoyalDarkBackground,
            onBackground = RoyalDarkOnBackground,
            surface = RoyalDarkSurface,
            onSurface = RoyalDarkOnSurface,
            surfaceVariant = RoyalDarkSurfaceVariant,
            onSurfaceVariant = RoyalDarkOnSurfaceVariant,
            outline = RoyalDarkOutline
        )
    ),
    PrestaFlowSkin.LAGOON to SkinPalette(
        light = lightColorScheme(
            primary = LagoonLightPrimary,
            onPrimary = LagoonLightOnPrimary,
            primaryContainer = LagoonLightPrimaryContainer,
            onPrimaryContainer = LagoonLightOnPrimaryContainer,
            secondary = LagoonLightSecondary,
            onSecondary = LagoonLightOnSecondary,
            secondaryContainer = LagoonLightSecondaryContainer,
            onSecondaryContainer = LagoonLightOnSecondaryContainer,
            tertiary = LagoonLightTertiary,
            onTertiary = LagoonLightOnTertiary,
            tertiaryContainer = LagoonLightTertiaryContainer,
            onTertiaryContainer = LagoonLightOnTertiaryContainer,
            error = LagoonLightError,
            onError = LagoonLightOnError,
            errorContainer = LagoonLightErrorContainer,
            onErrorContainer = LagoonLightOnErrorContainer,
            background = LagoonLightBackground,
            onBackground = LagoonLightOnBackground,
            surface = LagoonLightSurface,
            onSurface = LagoonLightOnSurface,
            surfaceVariant = LagoonLightSurfaceVariant,
            onSurfaceVariant = LagoonLightOnSurfaceVariant,
            outline = LagoonLightOutline
        ),
        dark = darkColorScheme(
            primary = LagoonDarkPrimary,
            onPrimary = LagoonDarkOnPrimary,
            primaryContainer = LagoonDarkPrimaryContainer,
            onPrimaryContainer = LagoonDarkOnPrimaryContainer,
            secondary = LagoonDarkSecondary,
            onSecondary = LagoonDarkOnSecondary,
            secondaryContainer = LagoonDarkSecondaryContainer,
            onSecondaryContainer = LagoonDarkOnSecondaryContainer,
            tertiary = LagoonDarkTertiary,
            onTertiary = LagoonDarkOnTertiary,
            tertiaryContainer = LagoonDarkTertiaryContainer,
            onTertiaryContainer = LagoonDarkOnTertiaryContainer,
            error = LagoonDarkError,
            onError = LagoonDarkOnError,
            errorContainer = LagoonDarkErrorContainer,
            onErrorContainer = LagoonDarkOnErrorContainer,
            background = LagoonDarkBackground,
            onBackground = LagoonDarkOnBackground,
            surface = LagoonDarkSurface,
            onSurface = LagoonDarkOnSurface,
            surfaceVariant = LagoonDarkSurfaceVariant,
            onSurfaceVariant = LagoonDarkOnSurfaceVariant,
            outline = LagoonDarkOutline
        )
    ),
    PrestaFlowSkin.EMBER to SkinPalette(
        light = lightColorScheme(
            primary = EmberLightPrimary,
            onPrimary = EmberLightOnPrimary,
            primaryContainer = EmberLightPrimaryContainer,
            onPrimaryContainer = EmberLightOnPrimaryContainer,
            secondary = EmberLightSecondary,
            onSecondary = EmberLightOnSecondary,
            secondaryContainer = EmberLightSecondaryContainer,
            onSecondaryContainer = EmberLightOnSecondaryContainer,
            tertiary = EmberLightTertiary,
            onTertiary = EmberLightOnTertiary,
            tertiaryContainer = EmberLightTertiaryContainer,
            onTertiaryContainer = EmberLightOnTertiaryContainer,
            error = EmberLightError,
            onError = EmberLightOnError,
            errorContainer = EmberLightErrorContainer,
            onErrorContainer = EmberLightOnErrorContainer,
            background = EmberLightBackground,
            onBackground = EmberLightOnBackground,
            surface = EmberLightSurface,
            onSurface = EmberLightOnSurface,
            surfaceVariant = EmberLightSurfaceVariant,
            onSurfaceVariant = EmberLightOnSurfaceVariant,
            outline = EmberLightOutline
        ),
        dark = darkColorScheme(
            primary = EmberDarkPrimary,
            onPrimary = EmberDarkOnPrimary,
            primaryContainer = EmberDarkPrimaryContainer,
            onPrimaryContainer = EmberDarkOnPrimaryContainer,
            secondary = EmberDarkSecondary,
            onSecondary = EmberDarkOnSecondary,
            secondaryContainer = EmberDarkSecondaryContainer,
            onSecondaryContainer = EmberDarkOnSecondaryContainer,
            tertiary = EmberDarkTertiary,
            onTertiary = EmberDarkOnTertiary,
            tertiaryContainer = EmberDarkTertiaryContainer,
            onTertiaryContainer = EmberDarkOnTertiaryContainer,
            error = EmberDarkError,
            onError = EmberDarkOnError,
            errorContainer = EmberDarkErrorContainer,
            onErrorContainer = EmberDarkOnErrorContainer,
            background = EmberDarkBackground,
            onBackground = EmberDarkOnBackground,
            surface = EmberDarkSurface,
            onSurface = EmberDarkOnSurface,
            surfaceVariant = EmberDarkSurfaceVariant,
            onSurfaceVariant = EmberDarkOnSurfaceVariant,
            outline = EmberDarkOutline
        )
    ),
    PrestaFlowSkin.FOREST to SkinPalette(
        light = lightColorScheme(
            primary = ForestLightPrimary,
            onPrimary = ForestLightOnPrimary,
            primaryContainer = ForestLightPrimaryContainer,
            onPrimaryContainer = ForestLightOnPrimaryContainer,
            secondary = ForestLightSecondary,
            onSecondary = ForestLightOnSecondary,
            secondaryContainer = ForestLightSecondaryContainer,
            onSecondaryContainer = ForestLightOnSecondaryContainer,
            tertiary = ForestLightTertiary,
            onTertiary = ForestLightOnTertiary,
            tertiaryContainer = ForestLightTertiaryContainer,
            onTertiaryContainer = ForestLightOnTertiaryContainer,
            error = ForestLightError,
            onError = ForestLightOnError,
            errorContainer = ForestLightErrorContainer,
            onErrorContainer = ForestLightOnErrorContainer,
            background = ForestLightBackground,
            onBackground = ForestLightOnBackground,
            surface = ForestLightSurface,
            onSurface = ForestLightOnSurface,
            surfaceVariant = ForestLightSurfaceVariant,
            onSurfaceVariant = ForestLightOnSurfaceVariant,
            outline = ForestLightOutline
        ),
        dark = darkColorScheme(
            primary = ForestDarkPrimary,
            onPrimary = ForestDarkOnPrimary,
            primaryContainer = ForestDarkPrimaryContainer,
            onPrimaryContainer = ForestDarkOnPrimaryContainer,
            secondary = ForestDarkSecondary,
            onSecondary = ForestDarkOnSecondary,
            secondaryContainer = ForestDarkSecondaryContainer,
            onSecondaryContainer = ForestDarkOnSecondaryContainer,
            tertiary = ForestDarkTertiary,
            onTertiary = ForestDarkOnTertiary,
            tertiaryContainer = ForestDarkTertiaryContainer,
            onTertiaryContainer = ForestDarkOnTertiaryContainer,
            error = ForestDarkError,
            onError = ForestDarkOnError,
            errorContainer = ForestDarkErrorContainer,
            onErrorContainer = ForestDarkOnErrorContainer,
            background = ForestDarkBackground,
            onBackground = ForestDarkOnBackground,
            surface = ForestDarkSurface,
            onSurface = ForestDarkOnSurface,
            surfaceVariant = ForestDarkSurfaceVariant,
            onSurfaceVariant = ForestDarkOnSurfaceVariant,
            outline = ForestDarkOutline
        )
    ),
    PrestaFlowSkin.SLATE to SkinPalette(
        light = lightColorScheme(
            primary = SlateLightPrimary,
            onPrimary = SlateLightOnPrimary,
            primaryContainer = SlateLightPrimaryContainer,
            onPrimaryContainer = SlateLightOnPrimaryContainer,
            secondary = SlateLightSecondary,
            onSecondary = SlateLightOnSecondary,
            secondaryContainer = SlateLightSecondaryContainer,
            onSecondaryContainer = SlateLightOnSecondaryContainer,
            tertiary = SlateLightTertiary,
            onTertiary = SlateLightOnTertiary,
            tertiaryContainer = SlateLightTertiaryContainer,
            onTertiaryContainer = SlateLightOnTertiaryContainer,
            error = SlateLightError,
            onError = SlateLightOnError,
            errorContainer = SlateLightErrorContainer,
            onErrorContainer = SlateLightOnErrorContainer,
            background = SlateLightBackground,
            onBackground = SlateLightOnBackground,
            surface = SlateLightSurface,
            onSurface = SlateLightOnSurface,
            surfaceVariant = SlateLightSurfaceVariant,
            onSurfaceVariant = SlateLightOnSurfaceVariant,
            outline = SlateLightOutline
        ),
        dark = darkColorScheme(
            primary = SlateDarkPrimary,
            onPrimary = SlateDarkOnPrimary,
            primaryContainer = SlateDarkPrimaryContainer,
            onPrimaryContainer = SlateDarkOnPrimaryContainer,
            secondary = SlateDarkSecondary,
            onSecondary = SlateDarkOnSecondary,
            secondaryContainer = SlateDarkSecondaryContainer,
            onSecondaryContainer = SlateDarkOnSecondaryContainer,
            tertiary = SlateDarkTertiary,
            onTertiary = SlateDarkOnTertiary,
            tertiaryContainer = SlateDarkTertiaryContainer,
            onTertiaryContainer = SlateDarkOnTertiaryContainer,
            error = SlateDarkError,
            onError = SlateDarkOnError,
            errorContainer = SlateDarkErrorContainer,
            onErrorContainer = SlateDarkOnErrorContainer,
            background = SlateDarkBackground,
            onBackground = SlateDarkOnBackground,
            surface = SlateDarkSurface,
            onSurface = SlateDarkOnSurface,
            surfaceVariant = SlateDarkSurfaceVariant,
            onSurfaceVariant = SlateDarkOnSurfaceVariant,
            outline = SlateDarkOutline
        )
    )
)

@StringRes
fun PrestaFlowSkin.displayNameRes(): Int = when (this) {
    PrestaFlowSkin.ROYAL -> R.string.skin_royal
    PrestaFlowSkin.LAGOON -> R.string.skin_lagoon
    PrestaFlowSkin.EMBER -> R.string.skin_ember
    PrestaFlowSkin.FOREST -> R.string.skin_forest
    PrestaFlowSkin.SLATE -> R.string.skin_slate
}

internal fun PrestaFlowSkin.colorScheme(darkTheme: Boolean): ColorScheme {
    val palette = palettes[this] ?: palettes.getValue(PrestaFlowSkin.ROYAL)
    return if (darkTheme) palette.dark else palette.light
}
