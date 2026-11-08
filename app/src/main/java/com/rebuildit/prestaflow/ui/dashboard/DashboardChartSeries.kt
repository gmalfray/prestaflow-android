package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint

/** Nature d'une série du graphique : détermine couleur, libellé et style côté UI. */
internal enum class ChartSeriesKind {
    TURNOVER,
    TURNOVER_PREVIOUS,
    AVG_CART,
    ORDERS_PAID,
    ORDERS_FREE,
    NEW_CUSTOMERS,
}

internal enum class ChartAxisSide { LEFT, RIGHT }

/** Unité d'une série : conditionne le formatage des labels d'axe et du tooltip. */
internal enum class ChartUnit { MONEY, COUNT }

/** Série prête à dessiner : une valeur par point du graphe, sur l'axe [side]. */
internal data class ChartSeriesData(
    val kind: ChartSeriesKind,
    val values: List<Double>,
    val side: ChartAxisSide,
    val unit: ChartUnit,
)

/**
 * Séries à tracer pour [mode]. Fonction pure (testable) : le panier moyen est calculé sur les
 * commandes PAYANTES (`orders - orders_free`), 0 si aucune.
 * [showNewCustomers] ne s'applique qu'à [DashboardChartMode.PAID_VS_FREE] (axe de comptage).
 */
internal fun buildChartSeries(
    mode: DashboardChartMode,
    points: List<DashboardChartPoint>,
    showNewCustomers: Boolean = false,
): List<ChartSeriesData> =
    when (mode) {
        DashboardChartMode.TURNOVER_VS_PREVIOUS ->
            listOf(
                ChartSeriesData(ChartSeriesKind.TURNOVER, points.map { it.turnover }, ChartAxisSide.LEFT, ChartUnit.MONEY),
                ChartSeriesData(
                    ChartSeriesKind.TURNOVER_PREVIOUS,
                    points.map { it.turnoverPrevious },
                    ChartAxisSide.LEFT,
                    ChartUnit.MONEY,
                ),
            )
        DashboardChartMode.TURNOVER_AND_AVG_CART ->
            listOf(
                ChartSeriesData(ChartSeriesKind.TURNOVER, points.map { it.turnover }, ChartAxisSide.LEFT, ChartUnit.MONEY),
                ChartSeriesData(ChartSeriesKind.AVG_CART, points.map { it.avgCartPaid }, ChartAxisSide.RIGHT, ChartUnit.MONEY),
            )
        DashboardChartMode.PAID_VS_FREE ->
            buildList {
                add(
                    ChartSeriesData(
                        ChartSeriesKind.ORDERS_PAID,
                        points.map { it.paidOrders.toDouble() },
                        ChartAxisSide.LEFT,
                        ChartUnit.COUNT,
                    ),
                )
                add(
                    ChartSeriesData(
                        ChartSeriesKind.ORDERS_FREE,
                        points.map { it.ordersFree.toDouble() },
                        ChartAxisSide.LEFT,
                        ChartUnit.COUNT,
                    ),
                )
                if (showNewCustomers) {
                    add(
                        ChartSeriesData(
                            ChartSeriesKind.NEW_CUSTOMERS,
                            points.map { it.newCustomers.toDouble() },
                            ChartAxisSide.LEFT,
                            ChartUnit.COUNT,
                        ),
                    )
                }
            }
        DashboardChartMode.AVG_CART ->
            listOf(
                ChartSeriesData(ChartSeriesKind.AVG_CART, points.map { it.avgCartPaid }, ChartAxisSide.LEFT, ChartUnit.MONEY),
            )
    }
