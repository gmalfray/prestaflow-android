package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardMetricsDto(
    @SerialName("turnover") val turnover: Double,
    @SerialName("orders_count") val ordersCount: Int,
    @SerialName("customers_count") val customersCount: Int,
    @SerialName("products_count") val productsCount: Int,
    @SerialName("chart") val chart: List<ChartPointDto>,
    /**
     * CA de la période précédente équivalente, retourné par le connecteur v1.5.x+.
     * Null si le connecteur ne supporte pas encore ce champ.
     */
    @SerialName("previous_turnover") val previousTurnover: Double? = null,
    /** Commandes à 0 € de la période, incluses dans [ordersCount] (connecteur récent). */
    @SerialName("orders_free_count") val ordersFreeCount: Int = 0,
    /** Commandes en attente de paiement (instantané, indépendant de la période). */
    @SerialName("pending_orders_count") val pendingOrdersCount: Int = 0,
    /** Produits actifs sous le seuil de stock bas (liste plafonnée à 50 côté connecteur). */
    @SerialName("low_stock_alerts") val lowStockAlerts: List<LowStockAlertDto> = emptyList(),
)

/** Entrée de `low_stock_alerts` ; seuls les champs utiles au dashboard sont mappés. */
@Serializable
data class LowStockAlertDto(
    @SerialName("product_id") val productId: Int = 0,
    @SerialName("quantity") val quantity: Int = 0,
)

@Serializable
data class ChartPointDto(
    @SerialName("label") val label: String,
    @SerialName("orders") val orders: Int,
    @SerialName("customers") val customers: Int,
    @SerialName("turnover") val turnover: Double,
    /**
     * Nouveaux clients inscrits dans ce bucket — champ v1.6.x+.
     * Valeur par défaut 0 : les caches existants (JSON sans ce champ) se désérialisent sans erreur.
     */
    @SerialName("new_customers") val newCustomers: Int = 0,
    /** Commandes à 0 € du bucket, incluses dans [orders]. */
    @SerialName("orders_free") val ordersFree: Int = 0,
    /** CA hors port du bucket de même rang l'an dernier (alignement positionnel), 0.0 si absent. */
    @SerialName("turnover_previous") val turnoverPrevious: Double = 0.0,
)

/** Réponse de `GET dashboard/free-conversion`. */
@Serializable
data class FreeConversionDto(
    @SerialName("free_first_customers") val freeFirstCustomers: Int = 0,
    @SerialName("paid_first_customers") val paidFirstCustomers: Int = 0,
    @SerialName("converted") val converted: Int = 0,
    @SerialName("rate") val rate: Double = 0.0,
)
