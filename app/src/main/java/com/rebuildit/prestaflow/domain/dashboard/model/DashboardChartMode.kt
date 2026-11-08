package com.rebuildit.prestaflow.domain.dashboard.model

/** Courbe affichée par le graphique du dashboard (choix persisté). */
enum class DashboardChartMode {
    /** CA vs CA de l'an dernier, même axe. */
    TURNOVER_VS_PREVIOUS,

    /** CA (axe gauche) + panier moyen des commandes payantes (axe droit). */
    TURNOVER_AND_AVG_CART,

    /** Commandes payantes vs commandes offertes. */
    PAID_VS_FREE,

    /** Panier moyen des commandes payantes seul. */
    AVG_CART,
    ;

    companion object {
        val DEFAULT = TURNOVER_VS_PREVIOUS
    }
}
