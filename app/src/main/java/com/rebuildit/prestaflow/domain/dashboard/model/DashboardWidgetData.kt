package com.rebuildit.prestaflow.domain.dashboard.model

/** Ligne du widget « Dernières commandes ». */
data class LatestOrder(
    val id: Long,
    val reference: String,
    val customerName: String,
    val totalPaid: Double,
    val currency: String,
    val status: String,
    val statusColor: String?,
    val createdAtIso: String?,
)

/** Ligne du widget « Top produits » (déclinaisons agrégées par produit). */
data class TopProduct(
    val productId: Int,
    val name: String,
    val quantity: Int,
    val totalTaxIncl: Double,
)

/** Paniers abandonnés non convertis en commande. */
data class AbandonedCartsSummary(
    val count: Int,
    val totalTaxIncl: Double,
)

/**
 * Avis en attente de modération. [hasMore] : le total n'est qu'un minorant (pagination sans
 * `total` côté pont, première page pleine).
 */
data class PendingReviewsCount(
    val count: Int,
    val hasMore: Boolean,
)

/** CA d'un univers de produits sur la période ([key] = clé stable du connecteur, ex. `kits`). */
data class UniverseSales(
    val key: String,
    val isDigital: Boolean,
    val caTaxIncl: Double,
    val quantity: Int,
)

/**
 * Ventes par univers. [universes] ne contient que les univers au CA > 0, triés par CA décroissant ;
 * [digitalShareRatio] (0..1) = CA des univers numériques / CA total (0 si aucune vente).
 */
data class SalesByUniverse(
    val universes: List<UniverseSales>,
    val freePatternsQuantity: Int,
    val digitalShareRatio: Float,
) {
    val totalTaxIncl: Double get() = universes.sumOf { it.caTaxIncl }
    val isEmpty: Boolean get() = universes.isEmpty()
}
