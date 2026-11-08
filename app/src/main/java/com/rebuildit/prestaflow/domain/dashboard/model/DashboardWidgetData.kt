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
