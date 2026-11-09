package com.rebuildit.prestaflow.domain.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.AbandonedCartsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct

/**
 * Données des widgets du dashboard à chargement réseau propre. Lecture seule, sans cache local :
 * chaque méthode lève en cas d'échec et c'est le widget concerné qui affiche l'erreur.
 */
interface DashboardWidgetsRepository {
    /** Les [limit] commandes les plus récentes, sans toucher au cache de l'écran Commandes. */
    suspend fun getLatestOrders(limit: Int): List<LatestOrder>

    /** Paniers sans commande, inactifs depuis [sinceDays] jours. */
    suspend fun getAbandonedCarts(sinceDays: Int): AbandonedCartsSummary

    /** Avis à modérer ; `null` si le module d'avis est absent de la boutique (HTTP 409). */
    suspend fun getPendingReviews(): PendingReviewsCount?

    /**
     * Meilleures ventes par quantité. [dateFrom] / [dateTo] au format `yyyy-MM-dd HH:mm:ss`
     * (null = toute la période).
     */
    suspend fun getTopProducts(
        limit: Int,
        dateFrom: String?,
        dateTo: String?,
    ): List<TopProduct>

    /**
     * CA par univers de produits sur la période ([dateFrom] / [dateTo] au format
     * `yyyy-MM-dd HH:mm:ss`, null = toute la période). `null` si l'endpoint est absent de la boutique
     * (connecteur pas à jour : HTTP 404 ou 409).
     */
    suspend fun getSalesByUnivers(
        dateFrom: String?,
        dateTo: String?,
    ): SalesByUniverse?
}
