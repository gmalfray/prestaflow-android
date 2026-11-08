package com.rebuildit.prestaflow.domain.dashboard.model

data class DashboardChartPoint(
    val label: String,
    val orders: Int,
    val customers: Int,
    val turnover: Double,
    /** Nouveaux clients inscrits dans le bucket. 0 si le connecteur < v1.6. */
    val newCustomers: Int = 0,
    /** Commandes à 0 € du bucket (incluses dans [orders]). */
    val ordersFree: Int = 0,
    /** CA hors port du bucket de même rang l'an dernier. 0.0 si absent. */
    val turnoverPrevious: Double = 0.0,
) {
    /** Commandes payantes du bucket (jamais négatif). */
    val paidOrders: Int get() = (orders - ordersFree).coerceAtLeast(0)

    /** Panier moyen sur les commandes payantes ; 0.0 sans commande payante. */
    val avgCartPaid: Double get() = averageCartPaid(turnover, orders, ordersFree)
}

/**
 * Panier moyen calculé sur les commandes payantes uniquement (les commandes offertes à 0 €
 * tireraient la moyenne vers le bas). 0.0 si aucune commande payante.
 */
fun averageCartPaid(
    turnover: Double,
    ordersCount: Int,
    ordersFreeCount: Int,
): Double {
    val paid = ordersCount - ordersFreeCount
    return if (paid > 0) turnover / paid else 0.0
}
