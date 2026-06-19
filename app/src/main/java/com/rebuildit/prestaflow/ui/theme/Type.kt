package com.rebuildit.prestaflow.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rebuildit.prestaflow.R

// ─────────────────────────────────────────────────────────────────────────────
// Typographie Terracotta — alignée sur design-tokens.json (Google Stitch).
// Police : Plus Jakarta Sans (OFL), assets statiques embarqués dans res/font/.
// Licence : docs/fonts/PlusJakartaSans-OFL.txt.
// ─────────────────────────────────────────────────────────────────────────────

private val PlusJakartaSans =
    FontFamily(
        Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    )

private val AppFontFamily = PlusJakartaSans

/**
 * Échelle typographique M3 mappée sur les tokens Stitch.
 *
 * Correspondances Stitch → M3 :
 *  - display-kpi (42sp/700)  → aucun slot M3 standard : utilisé directement via [kpiTextStyle]
 *  - headline-lg (28sp/700)  → headlineLarge
 *  - headline-md (22sp/600)  → headlineMedium / titleLarge
 *  - title-lg (18sp/600)     → titleMedium  (titleLarge M3 = 22sp, ici on redescend)
 *  - body-lg (16sp/400)      → bodyLarge
 *  - body-md (14sp/400)      → bodyMedium
 *  - label-lg (12sp/600)     → labelLarge (uppercase + letter-spacing dans le contexte d'usage)
 *  - label-md (11sp/500)     → labelSmall
 */
val AppTypography =
    Typography(
        // headline-lg Stitch : 28sp/700
        headlineLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        // headline-md Stitch : 22sp/600 — utilisé pour les montants KPI dans les cartes
        headlineMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        // Montants importants (ex. total période dans le graphique)
        headlineSmall =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                letterSpacing = 0.sp,
            ),
        // Titres de section dans les cartes : title-lg Stitch (18sp/600)
        titleLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        // Noms de produit/client, labels forts : 16sp/600
        titleMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        // Libellés secondaires, prix : 14sp/600
        titleSmall =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        // body-lg Stitch : 16sp/400
        bodyLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.sp,
            ),
        // body-md Stitch : 14sp/400
        bodyMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.sp,
            ),
        // label-lg Stitch : 12sp/600 — chips statut, badges, libellés KPI (uppercase dans les composants)
        labelLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        // label-md Stitch : 11sp/500
        labelMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/**
 * Style KPI display — 42sp/700, hors du système M3.
 * Utilisé pour les grands chiffres (chiffre d'affaires, etc.) dans les cartes KPI.
 * Pas de slot M3 correspondant : utiliser directement ce style dans les composants.
 */
val kpiTextStyle =
    TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
    )
