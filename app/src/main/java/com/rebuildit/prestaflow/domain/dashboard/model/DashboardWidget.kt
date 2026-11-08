package com.rebuildit.prestaflow.domain.dashboard.model

/** Largeur d'un widget : une demi-ligne (deux par ligne) ou la ligne entière. */
enum class DashboardWidgetSpan { HALF, FULL }

/**
 * Catalogue des widgets du dashboard. L'ordre de déclaration est l'ordre par défaut ; [id] est la
 * clé persistée et ne doit jamais changer une fois publié.
 */
enum class DashboardWidget(
    val id: String,
    val span: DashboardWidgetSpan,
    val defaultEnabled: Boolean,
) {
    TURNOVER("kpi_turnover", DashboardWidgetSpan.HALF, true),
    ORDERS("kpi_orders", DashboardWidgetSpan.HALF, true),
    AVG_CART("kpi_avg_cart", DashboardWidgetSpan.HALF, true),
    NEW_CUSTOMERS("kpi_new_customers", DashboardWidgetSpan.HALF, true),
    FREE_CONVERSION("free_conversion", DashboardWidgetSpan.FULL, true),
    CHART("chart", DashboardWidgetSpan.FULL, true),
    PENDING_ORDERS("kpi_pending_orders", DashboardWidgetSpan.HALF, false),
    LOW_STOCK("kpi_low_stock", DashboardWidgetSpan.HALF, false),
    ORDERS_SPLIT("orders_split", DashboardWidgetSpan.FULL, false),
    LATEST_ORDERS("latest_orders", DashboardWidgetSpan.FULL, false),
    CARTS("carts", DashboardWidgetSpan.HALF, false),
    REVIEWS("reviews", DashboardWidgetSpan.HALF, false),
    TOP_PRODUCTS("top_products", DashboardWidgetSpan.FULL, false),
    ;

    companion object {
        fun fromId(id: String): DashboardWidget? = entries.firstOrNull { it.id == id }
    }
}
