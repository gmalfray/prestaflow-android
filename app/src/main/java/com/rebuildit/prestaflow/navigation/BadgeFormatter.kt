package com.rebuildit.prestaflow.navigation

private const val BADGE_MAX_DISPLAYED = 99

/**
 * Libellé de pastille de navigation pour [count], ou `null` si aucune pastille ne doit être
 * affichée (compte nul ou négatif — jamais un badge "0").
 *
 * Plafonné à "99+" au-delà de [BADGE_MAX_DISPLAYED] pour ne pas faire exploser la largeur de la
 * pastille (97 fils SAV ouverts en prod aujourd'hui, cf. étude `rebuild-it/docs/app-avis-sav.md`).
 */
fun formatBadgeCount(count: Int): String? =
    when {
        count <= 0 -> null
        count > BADGE_MAX_DISPLAYED -> "$BADGE_MAX_DISPLAYED+"
        else -> count.toString()
    }
