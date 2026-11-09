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

/** Réponse de `GET reports/bestsellers` : une ligne par couple produit / déclinaison. */
@Serializable
data class BestSellersResponseDto(
    @SerialName("products") val products: List<BestSellerDto> = emptyList(),
)

@Serializable
data class BestSellerDto(
    @SerialName("product_id") val productId: Int = 0,
    @SerialName("product_attribute_id") val productAttributeId: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("reference") val reference: String? = null,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("total_tax_incl") val totalTaxIncl: Double = 0.0,
    @SerialName("total_tax_excl") val totalTaxExcl: Double = 0.0,
)

/** Réponse de `GET reports/sales-by-univers` : 7 univers fixes, plus le compteur de patrons gratuits. */
@Serializable
data class SalesByUniversResponseDto(
    @SerialName("universes") val universes: List<UniverseSalesDto> = emptyList(),
    @SerialName("free_patterns") val freePatterns: FreePatternsDto? = null,
)

@Serializable
data class UniverseSalesDto(
    @SerialName("key") val key: String = "",
    @SerialName("is_digital") val isDigital: Boolean = false,
    @SerialName("ca_tax_incl") val caTaxIncl: Double = 0.0,
    @SerialName("ca_tax_excl") val caTaxExcl: Double = 0.0,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("lines") val lines: Int = 0,
)

@Serializable
data class FreePatternsDto(
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("lines") val lines: Int = 0,
)

/** Réponse de `GET reports/customer-retention`. `median_days_between_orders` est null sans réachat. */
@Serializable
data class CustomerRetentionDto(
    @SerialName("paying_customers") val payingCustomers: Int = 0,
    @SerialName("repeat_customers") val repeatCustomers: Int = 0,
    @SerialName("repeat_rate") val repeatRate: Double? = null,
    @SerialName("median_days_between_orders") val medianDaysBetweenOrders: Double? = null,
    @SerialName("new_customers") val newCustomers: Int = 0,
    @SerialName("recurring_customers") val recurringCustomers: Int = 0,
)

/** Réponse de `GET reports/acquisition-channels` : commandes par dernier canal avant achat. */
@Serializable
data class AcquisitionChannelsResponseDto(
    @SerialName("channels") val channels: List<AcquisitionChannelDto> = emptyList(),
    @SerialName("window_days") val windowDays: Int? = null,
)

@Serializable
data class AcquisitionChannelDto(
    @SerialName("key") val key: String = "",
    @SerialName("orders") val orders: Int = 0,
)

/** Réponse de `GET dashboard/free-conversion-cohorts` : une cohorte par mois de 1re commande offerte. */
@Serializable
data class FreeConversionCohortsResponseDto(
    @SerialName("cohorts") val cohorts: List<FreeConversionCohortDto> = emptyList(),
)

@Serializable
data class FreeConversionCohortDto(
    @SerialName("month") val month: String = "",
    @SerialName("first_free_customers") val firstFreeCustomers: Int = 0,
    @SerialName("converted_30d") val converted30d: Int = 0,
    @SerialName("converted_60d") val converted60d: Int = 0,
    @SerialName("converted_90d") val converted90d: Int = 0,
)
