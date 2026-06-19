package com.rebuildit.prestaflow.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Tokens de spacing et de forme — alignés sur design-tokens.json (Google Stitch).
 * Base = 4dp. Marge de bord écran = 20dp. Gutter entre cartes = 16dp.
 */
object Dimensions {
    // Grille de spacing (base 4dp)
    val spacingXs = 4.dp // xs
    val spacingS = 8.dp // sm
    val spacingM = 16.dp // md  ← 16dp (Stitch)
    val spacingL = 24.dp // lg  ← 24dp (Stitch)
    val spacingXl = 32.dp // xl  ← 32dp (Stitch)

    // Layout
    val screenEdgeMargin = 20.dp // edge-margin Stitch
    val gutter = 16.dp // gutter entre éléments

    // Alias de compatibilité (anciens noms conservés le temps de la migration)
    @Deprecated("Utiliser screenEdgeMargin", ReplaceWith("screenEdgeMargin"))
    val screenHorizontalPadding = screenEdgeMargin

    @Deprecated("Utiliser spacingL", ReplaceWith("spacingL"))
    val spacingXxl = spacingL

    // Cartes
    val cardPadding = spacingM // padding interne des cartes = 16dp
    val cardCornerRadius = 20.dp // rayon signature (ROUND_EIGHT → 20dp Stitch)
    val chipCornerRadius = 8.dp // chips statut

    // Listes
    val listItemHeight = 72.dp // hauteur ligne produit/client
    val listItemSpacing = spacingM

    // Icônes
    val iconSizeSmall = 20.dp
    val iconSizeMedium = 24.dp
    val iconContainerSize = 40.dp // conteneur icône KPI card
    val avatarSize = 40.dp // avatar initiales commandes/clients

    // Cibles tactiles (minimum 48dp WCAG / Material 3)
    val minTouchTarget = 48.dp
}
