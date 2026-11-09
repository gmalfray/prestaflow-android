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

private const val PERCENT = 100.0

/**
 * Fidélité / réachat sur la période. [repeatRatePercent] est en pourcentage (37.8 = 37,8 %) ;
 * [medianDaysBetweenOrders] est null tant qu'aucun client n'a recommandé.
 */
data class CustomerRetention(
    val payingCustomers: Int,
    val repeatCustomers: Int,
    val repeatRatePercent: Double,
    val medianDaysBetweenOrders: Double?,
    val newCustomers: Int,
    val recurringCustomers: Int,
) {
    val isEmpty: Boolean get() = payingCustomers <= 0
}

/** Commandes d'un canal d'acquisition ([key] normalisée en minuscules : `direct`, `instagram`, utm libre…). */
data class AcquisitionChannel(
    val key: String,
    val orders: Int,
)

/** Commandes par canal, déjà filtrées (> 0) et triées par volume décroissant. */
data class AcquisitionChannels(
    val channels: List<AcquisitionChannel>,
    val windowDays: Int,
) {
    val totalOrders: Int get() = channels.sumOf { it.orders }
    val isEmpty: Boolean get() = channels.isEmpty()

    /** Part (0..1) d'un canal dans le total des commandes attribuées. */
    fun shareOf(channel: AcquisitionChannel): Float = if (totalOrders > 0) channel.orders.toFloat() / totalOrders else 0f
}

/** Cohorte de clients dont la 1re commande était offerte, regroupée par [month] (`yyyy-MM`). */
data class FreeConversionCohort(
    val month: String,
    val firstFreeCustomers: Int,
    val converted30d: Int,
    val converted60d: Int,
    val converted90d: Int,
) {
    /** Taux de conversion à 30 j, en pourcentage (0 si cohorte vide). */
    val rate30Percent: Double get() = ratePercent(converted30d)
    val rate60Percent: Double get() = ratePercent(converted60d)
    val rate90Percent: Double get() = ratePercent(converted90d)

    private fun ratePercent(converted: Int): Double = if (firstFreeCustomers > 0) converted * PERCENT / firstFreeCustomers else 0.0
}

/** Cohortes offert → payant, du mois le plus récent au plus ancien. */
data class FreeConversionCohorts(
    val cohorts: List<FreeConversionCohort>,
) {
    val isEmpty: Boolean get() = cohorts.isEmpty()
}
