package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget

/** Libellé du widget (carte du dashboard et ligne de l'éditeur). */
@StringRes
@Suppress("CyclomaticComplexMethod") // when exhaustif sur le catalogue
internal fun DashboardWidget.titleRes(): Int =
    when (this) {
        DashboardWidget.TURNOVER -> R.string.dashboard_kpi_turnover
        DashboardWidget.ORDERS -> R.string.dashboard_kpi_orders
        DashboardWidget.AVG_CART -> R.string.dashboard_kpi_avg_cart
        DashboardWidget.NEW_CUSTOMERS -> R.string.dashboard_kpi_customers
        DashboardWidget.FREE_CONVERSION -> R.string.dashboard_conversion_title
        DashboardWidget.CHART -> R.string.dashboard_widget_chart
        DashboardWidget.PENDING_ORDERS -> R.string.dashboard_widget_pending_orders
        DashboardWidget.LOW_STOCK -> R.string.dashboard_widget_low_stock
        DashboardWidget.ORDERS_SPLIT -> R.string.dashboard_chart_mode_paid_vs_free
        DashboardWidget.LATEST_ORDERS -> R.string.dashboard_widget_latest_orders
        DashboardWidget.CARTS -> R.string.dashboard_widget_carts
        DashboardWidget.REVIEWS -> R.string.dashboard_widget_reviews
        DashboardWidget.TOP_PRODUCTS -> R.string.dashboard_widget_top_products
        DashboardWidget.SALES_BY_UNIVERSE -> R.string.dashboard_widget_sales_by_universe
        DashboardWidget.CUSTOMER_RETENTION -> R.string.dashboard_widget_customer_retention
        DashboardWidget.ACQUISITION_CHANNELS -> R.string.dashboard_widget_acquisition_channels
        DashboardWidget.FREE_CONVERSION_COHORTS -> R.string.dashboard_widget_free_cohorts
        DashboardWidget.MONTHLY_TURNOVER -> R.string.dashboard_widget_monthly_turnover
        DashboardWidget.REVIEWS_SUMMARY -> R.string.dashboard_widget_reviews_summary
    }

@Suppress("CyclomaticComplexMethod")
internal fun DashboardWidget.icon(): ImageVector =
    when (this) {
        DashboardWidget.TURNOVER -> Icons.Outlined.Payments
        DashboardWidget.ORDERS -> Icons.Outlined.ShoppingBag
        DashboardWidget.AVG_CART -> Icons.Outlined.Analytics
        DashboardWidget.NEW_CUSTOMERS -> Icons.Outlined.Group
        DashboardWidget.FREE_CONVERSION -> Icons.Outlined.Percent
        DashboardWidget.CHART -> Icons.Outlined.QueryStats
        DashboardWidget.PENDING_ORDERS -> Icons.Outlined.PendingActions
        DashboardWidget.LOW_STOCK -> Icons.Outlined.Inventory2
        DashboardWidget.ORDERS_SPLIT -> Icons.Outlined.DonutLarge
        DashboardWidget.LATEST_ORDERS -> Icons.Outlined.Receipt
        DashboardWidget.CARTS -> Icons.Outlined.RemoveShoppingCart
        DashboardWidget.REVIEWS -> Icons.Outlined.RateReview
        DashboardWidget.TOP_PRODUCTS -> Icons.Outlined.EmojiEvents
        DashboardWidget.SALES_BY_UNIVERSE -> Icons.Outlined.BarChart
        DashboardWidget.CUSTOMER_RETENTION -> Icons.Outlined.Repeat
        DashboardWidget.ACQUISITION_CHANNELS -> Icons.Outlined.Campaign
        DashboardWidget.FREE_CONVERSION_COHORTS -> Icons.Outlined.CardGiftcard
        DashboardWidget.MONTHLY_TURNOVER -> Icons.Outlined.CalendarMonth
        DashboardWidget.REVIEWS_SUMMARY -> Icons.Outlined.Reviews
    }
