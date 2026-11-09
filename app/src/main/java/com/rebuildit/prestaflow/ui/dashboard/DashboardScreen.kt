package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidgetSpan
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversion
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.components.euroCurrencyFormatter
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Navigations déclenchées depuis les widgets réseau du dashboard. */
data class DashboardWidgetActions(
    val onOrderClick: (Long) -> Unit = {},
    val onAllOrdersClick: () -> Unit = {},
    val onCartsClick: () -> Unit = {},
    val onReviewsClick: () -> Unit = {},
    val onRetry: (DashboardWidget) -> Unit = {},
)

@Composable
fun DashboardRoute(
    onAddShop: () -> Unit = {},
    onOrdersClick: (DashboardPeriod) -> Unit = {},
    onClientsClick: (createdFrom: String?) -> Unit = {},
    widgetActions: DashboardWidgetActions = DashboardWidgetActions(),
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }
    DashboardScreen(
        modifier = modifier.fillMaxSize(),
        state = state,
        connections = connections,
        onPeriodSelected = viewModel::onPeriodSelected,
        onCustomRangeSelected = viewModel::onCustomRangeSelected,
        onRefresh = viewModel::onRefresh,
        onChartModeSelected = viewModel::onChartModeSelected,
        onConversionRetry = viewModel::loadConversion,
        onSwitchShop = shopsViewModel::switchShop,
        onAddShop = onAddShop,
        onOrdersClick = { onOrdersClick(state.selectedPeriod) },
        onClientsClick = onClientsClick,
        onWidgetLayoutSaved = viewModel::onWidgetLayoutSaved,
        widgetActions = widgetActions.copy(onRetry = viewModel::onWidgetRetry),
    )
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    state: DashboardUiState,
    connections: List<ShopConnection> = emptyList(),
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onCustomRangeSelected: (String, String) -> Unit = { _, _ -> },
    onRefresh: () -> Unit,
    onChartModeSelected: (DashboardChartMode) -> Unit = {},
    onConversionRetry: () -> Unit = {},
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
    onOrdersClick: () -> Unit = {},
    onClientsClick: (createdFrom: String?) -> Unit = {},
    onWidgetLayoutSaved: (DashboardLayout) -> Unit = {},
    widgetActions: DashboardWidgetActions = DashboardWidgetActions(),
) {
    val errorMessage = state.error?.asString()
    val hasSnapshot = state.snapshot != null
    val isLoading = state.isLoading && !hasSnapshot

    when {
        isLoading ->
            com.rebuildit.prestaflow.ui.components.LoadingState(modifier)
        hasSnapshot ->
            DashboardContent(
                modifier = modifier,
                snapshot = requireNotNull(state.snapshot),
                selectedPeriod = state.selectedPeriod,
                customRange = state.customRange,
                isRefreshing = state.isRefreshing,
                errorMessage = errorMessage,
                connections = connections,
                onPeriodSelected = onPeriodSelected,
                onCustomRangeSelected = onCustomRangeSelected,
                onRefresh = onRefresh,
                chartMode = state.chartMode,
                onChartModeSelected = onChartModeSelected,
                conversion = state.conversion,
                onConversionRetry = onConversionRetry,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
                onOrdersClick = onOrdersClick,
                onClientsClick = onClientsClick,
                widgetLayout = state.widgetLayout,
                onWidgetLayoutSaved = onWidgetLayoutSaved,
                widgetStates = state,
                widgetActions = widgetActions,
            )
        else ->
            DashboardEmptyState(
                modifier = modifier,
                errorMessage = errorMessage,
                onRetry = onRefresh,
            )
    }
}

// ─── État vide / erreur ───────────────────────────────────────────────────────

@Composable
private fun DashboardEmptyState(
    modifier: Modifier,
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimensions.screenEdgeMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_state_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(Dimensions.spacingM))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(Dimensions.spacingS))
                Button(onClick = onRetry) {
                    Text(text = stringResource(id = R.string.action_retry))
                }
            }
        }
    }
}

// ─── Contenu principal ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@Composable
private fun DashboardContent(
    modifier: Modifier,
    snapshot: DashboardSnapshot,
    selectedPeriod: DashboardPeriod,
    customRange: Pair<String, String>?,
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onCustomRangeSelected: (String, String) -> Unit,
    onRefresh: () -> Unit,
    chartMode: DashboardChartMode,
    onChartModeSelected: (DashboardChartMode) -> Unit,
    conversion: ConversionState,
    onConversionRetry: () -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    onOrdersClick: () -> Unit = {},
    onClientsClick: (createdFrom: String?) -> Unit = {},
    widgetLayout: DashboardLayout = DashboardLayout.DEFAULT,
    onWidgetLayoutSaved: (DashboardLayout) -> Unit = {},
    widgetStates: DashboardUiState = DashboardUiState(),
    widgetActions: DashboardWidgetActions = DashboardWidgetActions(),
) {
    val currencyFormatter = rememberCurrencyFormatter()
    val numberFormatter = rememberNumberFormatter()
    val turnoverText = remember(snapshot.turnover) { currencyFormatter.format(snapshot.turnover) }
    val ordersText = remember(snapshot.ordersCount) { numberFormatter.format(snapshot.ordersCount) }
    // Nouveaux clients = somme de newCustomers sur les points du graphe.
    // Cohérent avec la série "Nouveaux clients" du graphique.
    // Vaut 0 tant que le connecteur < v1.7 ne remplit pas ce champ.
    val newCustomersTotal = remember(snapshot.chart) { snapshot.chart.sumOf { it.newCustomers } }
    val newCustomersTotalText = remember(newCustomersTotal) { numberFormatter.format(newCustomersTotal) }
    // Panier moyen sur les commandes PAYANTES : les commandes offertes (0 €) le tireraient vers le bas.
    val avgCart =
        remember(snapshot.turnover, snapshot.ordersCount, snapshot.ordersFreeCount) { snapshot.avgCartPaid }
    val ordersFreeSubtitle =
        if (snapshot.ordersFreeCount > 0) {
            stringResource(id = R.string.dashboard_kpi_orders_free_sub, snapshot.ordersFreeCount)
        } else {
            null
        }
    val chartTotalText =
        when (chartMode) {
            DashboardChartMode.TURNOVER_VS_PREVIOUS, DashboardChartMode.TURNOVER_AND_AVG_CART -> turnoverText
            DashboardChartMode.AVG_CART -> currencyFormatter.format(avgCart)
            DashboardChartMode.PAID_VS_FREE ->
                numberFormatter.format((snapshot.ordersCount - snapshot.ordersFreeCount).coerceAtLeast(0))
        }
    val avgCartText = remember(avgCart) { currencyFormatter.format(avgCart) }
    val lastUpdatedText = remember(snapshot.lastUpdatedIso) { formatLastUpdated(snapshot.lastUpdatedIso) }

    // Mode plage libre = pas de "précédent" comparable → tendances masquées (mêmes conventions
    // que le comparatif de DashboardChartCard).
    val isCustomRange = customRange != null

    // Drill-down "Nouveaux clients" : borne created_from = début de la période affichée,
    // pour que la liste Clients corresponde EXACTEMENT au KPI (au lieu du 1er du mois civil figé).
    val newClientsCreatedFrom =
        remember(snapshot.period, customRange) {
            newCustomersCreatedFrom(snapshot.period, customRange)
        }

    // ── Sparklines par KPI (dérivées de snapshot.chart, aucun appel réseau) ────────────
    val turnoverSparkline = remember(snapshot.chart) { snapshot.chart.map { it.turnover.toFloat() } }
    val ordersSparkline = remember(snapshot.chart) { snapshot.chart.map { it.orders.toFloat() } }
    val newCustomersSparkline = remember(snapshot.chart) { snapshot.chart.map { it.newCustomers.toFloat() } }
    val avgCartSparkline =
        remember(snapshot.chart) {
            snapshot.chart.map { point -> point.avgCartPaid.toFloat() }
        }

    // ── Tendances par KPI (fonctions pures testables, cf. kpiTrendPercent*) ────────────
    val turnoverTrend =
        remember(snapshot.turnover, snapshot.previousTurnover, isCustomRange) {
            kpiTrendPercent(current = snapshot.turnover, previous = snapshot.previousTurnover, isCustomRange = isCustomRange)
        }
    val ordersTrend =
        remember(snapshot.chart, isCustomRange) {
            kpiTrendPercentFromSeries(snapshot.chart.map { it.orders.toDouble() }, isCustomRange)
        }
    val newCustomersTrend =
        remember(snapshot.chart, isCustomRange) {
            kpiTrendPercentFromSeries(snapshot.chart.map { it.newCustomers.toDouble() }, isCustomRange)
        }
    val avgCartTrend =
        remember(snapshot.chart, isCustomRange) {
            kpiTrendPercentFromSeries(
                snapshot.chart.map { point -> point.avgCartPaid },
                isCustomRange,
            )
        }

    val pendingOrdersText = remember(snapshot.pendingOrdersCount) { numberFormatter.format(snapshot.pendingOrdersCount) }
    val lowStockText =
        remember(snapshot.lowStockCount) {
            val count = numberFormatter.format(snapshot.lowStockCount)
            if (snapshot.lowStockCount >= LOW_STOCK_CAP) "$count+" else count
        }
    val kpiItems =
        mapOf(
            DashboardWidget.TURNOVER to
                KpiItem(
                    title = stringResource(id = DashboardWidget.TURNOVER.titleRes()),
                    value = turnoverText,
                    icon = DashboardWidget.TURNOVER.icon(),
                    trend = turnoverTrend,
                    sparklineValues = turnoverSparkline,
                ),
            DashboardWidget.ORDERS to
                KpiItem(
                    title = stringResource(id = DashboardWidget.ORDERS.titleRes()),
                    value = ordersText,
                    icon = DashboardWidget.ORDERS.icon(),
                    onClick = onOrdersClick,
                    subtitle = ordersFreeSubtitle,
                    trend = ordersTrend,
                    sparklineValues = ordersSparkline,
                ),
            DashboardWidget.AVG_CART to
                KpiItem(
                    title = stringResource(id = DashboardWidget.AVG_CART.titleRes()),
                    value = avgCartText,
                    icon = DashboardWidget.AVG_CART.icon(),
                    trend = avgCartTrend,
                    sparklineValues = avgCartSparkline,
                ),
            DashboardWidget.NEW_CUSTOMERS to
                KpiItem(
                    title = stringResource(id = DashboardWidget.NEW_CUSTOMERS.titleRes()),
                    value = newCustomersTotalText,
                    icon = DashboardWidget.NEW_CUSTOMERS.icon(),
                    onClick = { onClientsClick(newClientsCreatedFrom) },
                    trend = newCustomersTrend,
                    sparklineValues = newCustomersSparkline,
                ),
            DashboardWidget.PENDING_ORDERS to
                KpiItem(
                    title = stringResource(id = DashboardWidget.PENDING_ORDERS.titleRes()),
                    value = pendingOrdersText,
                    icon = DashboardWidget.PENDING_ORDERS.icon(),
                ),
            DashboardWidget.LOW_STOCK to
                KpiItem(
                    title = stringResource(id = DashboardWidget.LOW_STOCK.titleRes()),
                    value = lowStockText,
                    icon = DashboardWidget.LOW_STOCK.icon(),
                ),
        )
    val reviewsUnavailable = widgetStates.pendingReviews == WidgetLoad.Unavailable
    val universeUnavailable = widgetStates.salesByUniverse == WidgetLoad.Unavailable
    val retentionUnavailable = widgetStates.customerRetention == WidgetLoad.Unavailable
    val channelsUnavailable = widgetStates.acquisitionChannels == WidgetLoad.Unavailable
    val cohortsUnavailable = widgetStates.freeCohorts == WidgetLoad.Unavailable
    val widgetRows =
        remember(widgetLayout, reviewsUnavailable, universeUnavailable, retentionUnavailable, channelsUnavailable, cohortsUnavailable) {
            val hidden =
                buildSet {
                    if (reviewsUnavailable) add(DashboardWidget.REVIEWS)
                    if (universeUnavailable) add(DashboardWidget.SALES_BY_UNIVERSE)
                    if (retentionUnavailable) add(DashboardWidget.CUSTOMER_RETENTION)
                    if (channelsUnavailable) add(DashboardWidget.ACQUISITION_CHANNELS)
                    if (cohortsUnavailable) add(DashboardWidget.FREE_CONVERSION_COHORTS)
                }
            widgetLayout.rows(hidden = hidden)
        }
    val cartsSubtitle =
        (widgetStates.abandonedCarts as? WidgetLoad.Data)?.value?.takeIf { it.count > 0 }
            ?.let { stringResource(id = R.string.dashboard_widget_carts_sub, currencyFormatter.format(it.totalTaxIncl)) }
    val loadingText = stringResource(id = R.string.dashboard_widget_loading)
    val unavailableText = stringResource(id = R.string.dashboard_widget_unavailable_short)
    val cartsItem =
        KpiItem(
            title = stringResource(id = DashboardWidget.CARTS.titleRes()),
            value =
                widgetValueText(widgetStates.abandonedCarts, loadingText, unavailableText) { numberFormatter.format(it.count) },
            icon = DashboardWidget.CARTS.icon(),
            onClick =
                if (widgetStates.abandonedCarts == WidgetLoad.Error) {
                    { widgetActions.onRetry(DashboardWidget.CARTS) }
                } else {
                    widgetActions.onCartsClick
                },
            subtitle = cartsSubtitle,
            status = widgetStates.abandonedCarts.tileStatus(),
        )
    val reviewsItem =
        KpiItem(
            title = stringResource(id = DashboardWidget.REVIEWS.titleRes()),
            value =
                widgetValueText(widgetStates.pendingReviews, loadingText, unavailableText) {
                    numberFormatter.format(it.count) + if (it.hasMore) "+" else ""
                },
            icon = DashboardWidget.REVIEWS.icon(),
            onClick =
                if (widgetStates.pendingReviews == WidgetLoad.Error) {
                    { widgetActions.onRetry(DashboardWidget.REVIEWS) }
                } else {
                    widgetActions.onReviewsClick
                },
            status = widgetStates.pendingReviews.tileStatus(),
        )

    var showLayoutEditor by rememberSaveable { mutableStateOf(false) }

    // ── Graphique en plein écran (volet 3) ──────────────────────────────────────────
    var showFullscreenChart by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        ) {
            val renderWidget: @Composable (DashboardWidget) -> Unit = { widget ->
                val cellModifier = Modifier.fillMaxWidth()
                when (widget) {
                    DashboardWidget.FREE_CONVERSION ->
                        FreeConversionCard(modifier = cellModifier, state = conversion, onRetry = onConversionRetry)
                    DashboardWidget.CHART ->
                        DashboardChartCard(
                            modifier = cellModifier,
                            points = snapshot.chart,
                            mode = chartMode,
                            onModeSelected = onChartModeSelected,
                            totalText = chartTotalText,
                            currentTurnover = snapshot.turnover,
                            previousTurnover = snapshot.previousTurnover,
                            // En mode plage libre, on n'affiche pas le comparatif "vs même période l'an dernier"
                            selectedPeriod = if (customRange == null) selectedPeriod else null,
                            onExpandClick = { showFullscreenChart = true },
                        )
                    DashboardWidget.ORDERS_SPLIT ->
                        DashboardOrdersSplitCard(
                            modifier = cellModifier,
                            ordersCount = snapshot.ordersCount,
                            ordersFreeCount = snapshot.ordersFreeCount,
                        )
                    DashboardWidget.LATEST_ORDERS ->
                        LatestOrdersCard(
                            modifier = cellModifier,
                            state = widgetStates.latestOrders,
                            onRetry = { widgetActions.onRetry(DashboardWidget.LATEST_ORDERS) },
                            onOrderClick = widgetActions.onOrderClick,
                            onHeaderClick = widgetActions.onAllOrdersClick,
                        )
                    DashboardWidget.TOP_PRODUCTS ->
                        TopProductsCard(
                            modifier = cellModifier,
                            state = widgetStates.topProducts,
                            onRetry = { widgetActions.onRetry(DashboardWidget.TOP_PRODUCTS) },
                        )
                    DashboardWidget.SALES_BY_UNIVERSE ->
                        SalesByUniverseCard(
                            modifier = cellModifier,
                            state = widgetStates.salesByUniverse,
                            onRetry = { widgetActions.onRetry(DashboardWidget.SALES_BY_UNIVERSE) },
                        )
                    DashboardWidget.CUSTOMER_RETENTION ->
                        CustomerRetentionCard(
                            modifier = cellModifier,
                            state = widgetStates.customerRetention,
                            onRetry = { widgetActions.onRetry(DashboardWidget.CUSTOMER_RETENTION) },
                        )
                    DashboardWidget.ACQUISITION_CHANNELS ->
                        AcquisitionChannelsCard(
                            modifier = cellModifier,
                            state = widgetStates.acquisitionChannels,
                            onRetry = { widgetActions.onRetry(DashboardWidget.ACQUISITION_CHANNELS) },
                        )
                    DashboardWidget.FREE_CONVERSION_COHORTS ->
                        FreeCohortsCard(
                            modifier = cellModifier,
                            state = widgetStates.freeCohorts,
                            onRetry = { widgetActions.onRetry(DashboardWidget.FREE_CONVERSION_COHORTS) },
                        )
                    else -> {
                        val item =
                            when (widget) {
                                DashboardWidget.CARTS -> cartsItem
                                DashboardWidget.REVIEWS -> reviewsItem
                                else -> kpiItems[widget]
                            }
                        if (item != null) KpiCard(modifier = cellModifier, item = item)
                    }
                }
            }

            // Ordre local des widgets affichés, mis à jour pendant le glisser et persisté au relâché.
            val displayedWidgets = remember(widgetRows) { widgetRows.flatten() }
            val orderedWidgets = remember(displayedWidgets) { mutableStateListOf<DashboardWidget>().apply { addAll(displayedWidgets) } }
            val gridState = rememberLazyGridState()
            val haptic = LocalHapticFeedback.current
            val reorderableState =
                rememberReorderableLazyGridState(gridState) { from, to ->
                    val fromIndex = orderedWidgets.indexOfFirst { it.id == from.key }
                    val toIndex = orderedWidgets.indexOfFirst { it.id == to.key }
                    if (fromIndex >= 0 && toIndex >= 0) {
                        orderedWidgets.add(toIndex, orderedWidgets.removeAt(fromIndex))
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding =
                    PaddingValues(
                        start = Dimensions.screenEdgeMargin,
                        end = Dimensions.screenEdgeMargin,
                        bottom = 100.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimensions.gutter),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.gutter),
            ) {
                // En-tête intégré dans la grille (pleine largeur, non déplaçable). Les marges latérales
                // de la grille sont compensées pour garder l'en-tête bord à bord comme avant.
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    DashboardHeader(
                        modifier =
                            Modifier.layout { measurable, constraints ->
                                val margin = Dimensions.screenEdgeMargin.roundToPx()
                                val placeable = measurable.measure(constraints.offset(horizontal = 2 * margin))
                                layout(constraints.maxWidth, placeable.height) { placeable.place(-margin, 0) }
                            },
                        connections = connections,
                        selectedPeriod = selectedPeriod,
                        customRange = customRange,
                        onPeriodSelected = onPeriodSelected,
                        onCustomRangeSelected = onCustomRangeSelected,
                        onSwitchShop = onSwitchShop,
                        onAddShop = onAddShop,
                        onEditLayout = { showLayoutEditor = true },
                    )
                }

                // Bandeau erreur inline
                if (errorMessage != null) {
                    item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        ErrorBanner(message = errorMessage, onRetry = onRefresh)
                    }
                }

                // Widgets visibles : HALF = 1 colonne, FULL = pleine largeur. Appui long = déplacement.
                items(
                    orderedWidgets.toList(),
                    key = { it.id },
                    span = { widget ->
                        GridItemSpan(if (widget.span == DashboardWidgetSpan.HALF) 1 else maxLineSpan)
                    },
                ) { widget ->
                    ReorderableItem(reorderableState, key = widget.id) { isDragging ->
                        val scale by animateFloatAsState(if (isDragging) DRAG_LIFT_SCALE else 1f, label = "widgetScale")
                        val elevation by animateDpAsState(if (isDragging) DRAG_LIFT_ELEVATION else 0.dp, label = "widgetElevation")
                        Box(
                            modifier =
                                Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .shadow(elevation, RoundedCornerShape(Dimensions.cardCornerRadius))
                                    .longPressDraggableHandle(
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (orderedWidgets.toList() != displayedWidgets) {
                                                onWidgetLayoutSaved(widgetLayout.withVisibleOrder(orderedWidgets.toList()))
                                            }
                                        },
                                    ),
                        ) {
                            renderWidget(widget)
                        }
                    }
                }

                // Dernière synchronisation
                item(key = "last_sync", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        modifier = Modifier.padding(top = Dimensions.spacingS, bottom = Dimensions.spacingL),
                        text = stringResource(id = R.string.label_last_sync, lastUpdatedText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (showLayoutEditor) {
            DashboardLayoutEditorOverlay(
                layout = widgetLayout,
                onSave = { layout ->
                    onWidgetLayoutSaved(layout)
                    showLayoutEditor = false
                },
                onDismiss = { showLayoutEditor = false },
            )
        }
    } // fin du Box racine

    // Graphique plein écran — ouvert depuis DashboardChartCard (volet 3)
    if (showFullscreenChart) {
        DashboardChartFullscreenDialog(
            points = snapshot.chart,
            mode = chartMode,
            totalText = chartTotalText,
            onDismiss = { showFullscreenChart = false },
        )
    }
}

private const val DRAG_LIFT_SCALE = 1.03f
private val DRAG_LIFT_ELEVATION = 12.dp

/**
 * Nouvelle disposition après un glisser sur l'accueil : les widgets affichés ([order]) reprennent,
 * dans le nouvel ordre, les emplacements qu'ils occupaient ; les autres (masqués, ou exclus
 * momentanément comme les avis indisponibles) restent exactement où ils étaient.
 */
internal fun DashboardLayout.withVisibleOrder(order: List<DashboardWidget>): DashboardLayout {
    val byWidget = items.associateBy { it.widget }
    val shown = order.toSet()
    val queue = order.iterator()
    return DashboardLayout(
        items.map { item ->
            if (item.widget in shown && queue.hasNext()) byWidget.getValue(queue.next()) else item
        },
    )
}

// ─── En-tête ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardHeader(
    connections: List<ShopConnection>,
    selectedPeriod: DashboardPeriod,
    customRange: Pair<String, String>?,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onCustomRangeSelected: (String, String) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    onEditLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeShop = connections.firstOrNull { it.isActive }
    val shopName =
        activeShop?.label
            ?: activeShop?.shopUrl?.substringAfter("://")?.trimEnd('/')
            ?: stringResource(id = R.string.app_name)

    // État du DateRangePicker
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val startMs = datePickerState.selectedStartDateMillis
                        if (startMs != null) {
                            // Fin optionnelle : sans date de fin, on valide une journée unique
                            // (from = to = début) pour ne pas bloquer l'utilisateur.
                            val endMs = datePickerState.selectedEndDateMillis ?: startMs
                            val from =
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(startMs),
                                    ZoneOffset.UTC,
                                ).toString()
                            val to =
                                LocalDate.ofInstant(
                                    Instant.ofEpochMilli(endMs),
                                    ZoneOffset.UTC,
                                ).toString()
                            onCustomRangeSelected(from, to)
                        }
                        showDatePicker = false
                    },
                    // Activé dès qu'une date de début est choisie (la fin est optionnelle).
                    enabled = datePickerState.selectedStartDateMillis != null,
                ) {
                    Text(text = stringResource(id = R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(id = R.string.action_cancel))
                }
            },
        ) {
            DateRangePicker(
                state = datePickerState,
                title = {
                    Text(
                        text = stringResource(id = R.string.dashboard_date_picker_title),
                        modifier = Modifier.padding(start = 64.dp, end = 12.dp, top = 16.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin)
                    .padding(top = Dimensions.spacingL, bottom = Dimensions.spacingM),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            // Ligne salutation + bouton refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.dashboard_greeting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // En mode plage libre, afficher les dates sous le nom de la boutique
                    val subtitleText =
                        if (customRange != null) {
                            formatCustomRangeDisplay(customRange.first, customRange.second)
                        } else {
                            null
                        }
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    // Sélecteur de boutique — chip sous le titre (visible si connexion définie)
                    if (connections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimensions.spacingXs))
                        ShopSwitcherChip(
                            connections = connections,
                            onSwitch = onSwitchShop,
                            onAddShop = onAddShop,
                        )
                    }
                }
                IconButton(onClick = onEditLayout) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(id = R.string.dashboard_edit_action),
                    )
                }
            }

            // Sélecteur de période : chips pill horizontaux + chip "Personnalisé"
            PeriodChipRow(
                selectedPeriod = selectedPeriod,
                isCustomMode = customRange != null,
                onPeriodSelected = onPeriodSelected,
                onCustomRangeClick = { showDatePicker = true },
            )
        }
    }
}

// ─── Chips période ────────────────────────────────────────────────────────────

@Composable
private fun PeriodChipRow(
    selectedPeriod: DashboardPeriod,
    isCustomMode: Boolean,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onCustomRangeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        DashboardPeriod.values().forEach { period ->
            val isSelected = !isCustomMode && period == selectedPeriod
            val label =
                when (period) {
                    DashboardPeriod.TODAY -> stringResource(id = R.string.dashboard_period_today)
                    DashboardPeriod.WEEK -> stringResource(id = R.string.dashboard_period_week)
                    DashboardPeriod.MONTH -> stringResource(id = R.string.dashboard_period_month)
                    DashboardPeriod.QUARTER -> stringResource(id = R.string.dashboard_period_quarter)
                    DashboardPeriod.YEAR -> stringResource(id = R.string.dashboard_period_year)
                }
            PeriodChip(
                label = label,
                isSelected = isSelected,
                onClick = { onPeriodSelected(period) },
            )
        }
        // Chip "Personnalisé" — toujours à la fin
        PeriodChip(
            label = stringResource(id = R.string.dashboard_period_custom),
            isSelected = isCustomMode,
            onClick = onCustomRangeClick,
        )
    }
}

@Composable
private fun PeriodChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .height(Dimensions.minTouchTarget)
                .clip(CircleShape),
        shape = CircleShape,
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        contentColor =
            if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        onClick = onClick,
        tonalElevation = if (isSelected) 0.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Dimensions.spacingL, vertical = Dimensions.spacingS),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// ─── Grille KPI ───────────────────────────────────────────────────────────────

/**
 * Carte KPI Terracotta — fond blanc, icône dans un conteneur teinté en haut-gauche,
 * badge de tendance en haut-droite (si disponible), label uppercase, grande valeur en gras,
 * mini-sparkline discrète en bas de carte.
 * Hauteur compacte fixe ([Dimensions.kpiCardHeight]) plutôt qu'un ratio carré : évite les
 * pavés vides en paysage tout en gardant les cartes d'une même ligne alignées.
 * Rayon 20dp signature du design system.
 * Cliquable si [item.onClick] est non nul (navigation vers la liste filtrée).
 */
@Composable
private fun KpiCard(
    item: KpiItem,
    modifier: Modifier = Modifier,
) {
    val cardDesc = stringResource(id = R.string.dashboard_content_description_kpi_card, item.title, item.value)
    val trendDesc = item.trend?.let { trend -> kpiTrendContentDescription(trend) }
    val fullCardDesc =
        listOfNotNull(cardDesc, item.subtitle, trendDesc).joinToString(", ")
    val cardShape = RoundedCornerShape(Dimensions.cardCornerRadius)
    val cardColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    val cardElevation = CardDefaults.cardElevation(defaultElevation = 2.dp)

    val innerContent: @Composable () -> Unit = {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Dimensions.cardPadding)
                    .semantics(mergeDescendants = true) {
                        contentDescription = fullCardDesc
                        if (item.onClick != null) role = Role.Button
                    },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Icône teintée (haut-gauche) + badge de tendance (haut-droite, si disponible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(Dimensions.iconContainerSize)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimensions.iconSizeMedium),
                    )
                }
                if (item.trend != null) {
                    KpiTrendBadge(trend = item.trend)
                }
            }

            // Label + valeur — la valeur reste la pièce visuelle la plus imposante de la carte
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 2 lignes en filet de sécurité pour les langues à libellés longs (DE/NL) :
                    // évite la troncature « DURCHSCHNITTLICHER BE… » vue en recette i18n.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                when (item.status) {
                    TileStatus.Loading -> SkeletonBar(modifier = Modifier.fillMaxWidth(0.5f).padding(vertical = 5.dp), height = 18.dp)
                    TileStatus.Unavailable ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(Dimensions.iconSizeSmall),
                            )
                            Text(
                                text = item.value,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    TileStatus.Ready ->
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                }
                if (item.subtitle != null && item.status == TileStatus.Ready) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Mini-sparkline — dégradation silencieuse si série vide/trop courte/plate (cf. KpiSparkline)
            KpiSparkline(
                values = item.sparklineValues,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Dimensions.sparklineHeight),
            )
        }
    }

    if (item.onClick != null) {
        Card(
            onClick = item.onClick,
            modifier = modifier.height(Dimensions.kpiCardHeight),
            shape = cardShape,
            colors = cardColors,
            elevation = cardElevation,
        ) { innerContent() }
    } else {
        Card(
            modifier = modifier.height(Dimensions.kpiCardHeight),
            shape = cardShape,
            colors = cardColors,
            elevation = cardElevation,
        ) { innerContent() }
    }
}

/**
 * Badge pilule de tendance — flèche + pourcentage, vert (hausse) / rouge (baisse).
 * [percent] est déjà signé (positif = hausse, négatif = baisse) ; l'appelant est responsable
 * de masquer le badge (passer `null` côté [KpiItem.trendPercent]) quand la donnée n'est pas
 * fiable (série trop courte, dénominateur nul, mode plage libre).
 */
@Composable
private fun KpiTrendBadge(
    trend: KpiTrend,
    modifier: Modifier = Modifier,
) {
    val isPositive = trend.isPositive
    val color = if (isPositive) kpiTrendPositiveColor else kpiTrendNegativeColor
    val icon = if (isPositive) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = formatKpiTrend(trend),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

/**
 * Mini-courbe de tendance, tracée à partir de [values] normalisées sur toute la zone
 * disponible. Dégradation silencieuse : ne dessine rien si la série est vide, trop courte
 * (< 2 points), ou plate (tous les points égaux) — jamais de crash ni de ligne trompeuse.
 */
@Composable
private fun KpiSparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (values.size < 2) return
    val minValue = values.min()
    val maxValue = values.max()
    if (maxValue <= minValue) return
    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val offsets =
            values.mapIndexed { index, value ->
                val normalized = (value - minValue) / (maxValue - minValue)
                Offset(stepX * index, size.height - normalized * size.height)
            }
        drawPath(
            path = catmullRomPath(offsets),
            color = color,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private val kpiTrendPositiveColor = Color(0xFF4CBB6C)
private val kpiTrendNegativeColor = Color(0xFFFF4C4C)

/** Formate un pourcentage de tendance déjà signé, ex : `12.3%` (valeur absolue, le signe est porté par la couleur/flèche). */
private fun formatTrendPercent(percent: Double): String = "${"%.1f".format(abs(percent))}%"

/** Texte du badge : pourcentage normal, « nouv. » (base N-1 nulle) ou « ≥ +1000 % » (variation énorme). */
@Composable
private fun formatKpiTrend(trend: KpiTrend): String =
    when (trend) {
        is KpiTrend.Percent -> formatTrendPercent(trend.value)
        KpiTrend.New -> stringResource(id = R.string.dashboard_kpi_trend_new)
        KpiTrend.Huge -> stringResource(id = R.string.dashboard_kpi_trend_huge)
    }

@Composable
private fun kpiTrendContentDescription(trend: KpiTrend): String =
    when (trend) {
        is KpiTrend.Percent -> {
            val resId = if (trend.isPositive) R.string.dashboard_kpi_trend_up else R.string.dashboard_kpi_trend_down
            stringResource(id = resId, formatTrendPercent(trend.value))
        }
        KpiTrend.New -> stringResource(id = R.string.dashboard_kpi_trend_new_desc)
        KpiTrend.Huge -> stringResource(id = R.string.dashboard_kpi_trend_huge_desc)
    }

/** Bandeau « +x % vs même période l'an dernier » du graphe, assaini par la même règle que les badges KPI. */
@Composable
private fun ChartComparisonBanner(
    trend: KpiTrend?,
    period: DashboardPeriod,
) {
    val isPositive = trend?.isPositive ?: true
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                when (trend) {
                    is KpiTrend.Percent -> "${if (isPositive) "+" else ""}${"%.1f".format(trend.value)}%"
                    null -> "0.0%"
                    else -> formatKpiTrend(trend)
                },
            style = MaterialTheme.typography.labelLarge,
            color = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(id = periodComparisonLabelRes(period)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Carte graphique ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardChartCard(
    points: List<DashboardChartPoint>,
    mode: DashboardChartMode,
    onModeSelected: (DashboardChartMode) -> Unit,
    totalText: String,
    currentTurnover: Double,
    previousTurnover: Double?,
    /**
     * Null en mode plage libre : le comparatif "vs même période l'an dernier" n'est alors pas affiché.
     */
    selectedPeriod: DashboardPeriod?,
    /** Ouvre le graphique en plein écran (volet 3). Ignoré si [points] est vide. */
    onExpandClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Toggle 3ᵉ série "Nouveaux clients" — local à la carte
    var showNewCustomers by remember { mutableStateOf(false) }

    Card(
        onClick = { if (points.isNotEmpty()) onExpandClick() },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.spacingL),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingL),
        ) {
            // En-tête : titre + tendance à gauche, total à droite. weight(fill=false) sur les
            // deux colonnes : le total en headlineMedium peut devenir large à fontScale élevé —
            // chacune est plafonnée à sa part de largeur au lieu de chevaucher l'autre.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(id = chartModeTitleRes(mode)),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Comparatif vs même période l'an dernier (N-1) — masqué en mode plage libre
                    // et pour les courbes qui ne portent pas sur le CA
                    if (previousTurnover != null && selectedPeriod != null && mode.isTurnoverBased()) {
                        ChartComparisonBanner(
                            trend = kpiTrendPercent(currentTurnover, previousTurnover, isCustomRange = false),
                            period = selectedPeriod,
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = totalText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(id = R.string.dashboard_chart_total_period),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ChartModeSelector(selected = mode, onSelected = onModeSelected)

            // Icône agrandir + toggle 3ᵉ série : chip compact "Nouveaux clients"
            // (la superposition ne s'applique qu'à l'axe de comptage : courbe payantes/offertes)
            if (points.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onExpandClick,
                        modifier = Modifier.size(Dimensions.minTouchTarget),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Fullscreen,
                            contentDescription =
                                stringResource(
                                    id = R.string.dashboard_chart_expand_content_description,
                                ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.iconSizeSmall),
                        )
                    }
                    if (mode == DashboardChartMode.PAID_VS_FREE) {
                        FilterChip(
                            selected = showNewCustomers,
                            onClick = { showNewCustomers = !showNewCustomers },
                            label = {
                                Text(
                                    text = stringResource(id = R.string.dashboard_chart_new_customers),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorNewCustomers.copy(alpha = 0.15f),
                                    selectedLabelColor = colorNewCustomers,
                                ),
                            // Le libellé (« Nouveaux clients ») peut devenir large à fontScale élevé ;
                            // borné pour ne pas déborder hors de la carte à côté du bouton plein écran.
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            // Graphique ou état vide
            if (points.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.dashboard_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                DashboardLineChart(
                    points = points,
                    mode = mode,
                    height = 160.dp,
                    showNewCustomers = showNewCustomers && mode == DashboardChartMode.PAID_VS_FREE,
                )
            }
        }
    }
}

private fun DashboardChartMode.isTurnoverBased(): Boolean =
    this == DashboardChartMode.TURNOVER_VS_PREVIOUS || this == DashboardChartMode.TURNOVER_AND_AVG_CART

private fun chartModeTitleRes(mode: DashboardChartMode): Int =
    when (mode) {
        DashboardChartMode.TURNOVER_VS_PREVIOUS -> R.string.dashboard_chart_title_turnover
        DashboardChartMode.TURNOVER_AND_AVG_CART -> R.string.dashboard_chart_mode_turnover_avg_cart
        DashboardChartMode.PAID_VS_FREE -> R.string.dashboard_chart_mode_paid_vs_free
        DashboardChartMode.AVG_CART -> R.string.dashboard_chart_mode_avg_cart
    }

private fun chartModeChipRes(mode: DashboardChartMode): Int =
    when (mode) {
        DashboardChartMode.TURNOVER_VS_PREVIOUS -> R.string.dashboard_chart_mode_turnover_vs_previous
        DashboardChartMode.TURNOVER_AND_AVG_CART -> R.string.dashboard_chart_mode_turnover_avg_cart
        DashboardChartMode.PAID_VS_FREE -> R.string.dashboard_chart_mode_paid_vs_free
        DashboardChartMode.AVG_CART -> R.string.dashboard_chart_mode_avg_cart
    }

/** Sélecteur de courbe : chips défilables horizontalement (libellés longs en DE/NL). */
@Composable
private fun ChartModeSelector(
    selected: DashboardChartMode,
    onSelected: (DashboardChartMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        DashboardChartMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                label = {
                    Text(
                        text = stringResource(id = chartModeChipRes(mode)),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

// ─── Carte conversion offert → payant ─────────────────────────────────────────

@Composable
private fun FreeConversionCard(
    state: ConversionState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            WidgetCardHeader(widget = DashboardWidget.FREE_CONVERSION)
            when (state) {
                ConversionState.Loading -> WidgetSkeleton(lines = 2)
                ConversionState.Error ->
                    WidgetErrorRow(message = stringResource(id = R.string.dashboard_conversion_error), onRetry = onRetry)
                is ConversionState.Data -> FreeConversionData(state.conversion)
            }
        }
    }
}

@Composable
private fun FreeConversionData(conversion: FreeConversion) {
    if (conversion.freeFirstCustomers <= 0) {
        Text(
            text = stringResource(id = R.string.dashboard_conversion_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Text(
        text = stringResource(id = R.string.dashboard_conversion_rate, conversion.rate),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(id = R.string.dashboard_conversion_detail, conversion.converted, conversion.freeFirstCustomers),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ─── Graphique plein écran (volet 3) ───────────────────────────────────────────

/**
 * Graphique en plein écran, ouvert depuis [DashboardChartCard] (icône agrandir ou tap sur la
 * carte). Force l'orientation paysage à l'ouverture et restaure l'orientation d'origine de
 * l'activité à la fermeture — l'app est verrouillée en portrait au niveau du manifeste
 * ([android:screenOrientation="sensorPortrait"]), donc ce forçage est local à cet écran.
 */
@Composable
private fun DashboardChartFullscreenDialog(
    points: List<DashboardChartPoint>,
    mode: DashboardChartMode,
    totalText: String,
    onDismiss: () -> Unit,
) {
    // NB : on NE force PAS l'orientation paysage. `MainActivity` se recrée au changement
    // d'orientation → forcer requestedOrientation ici provoquait une boucle de rotation infinie.
    // Le dialog remplit déjà l'écran ; l'auto-rotation du système gère le paysage si l'utilisateur
    // tourne le téléphone.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(Dimensions.spacingM),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // weight(1f) : seule colonne pondérée du Row, elle absorbe l'espace restant
                    // sans jamais pousser le bouton fermer (non pondéré) hors de l'écran, quel que
                    // soit le fontScale — même piège que le crayon d'édition des commandes.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = chartModeTitleRes(mode)),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = totalText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(id = R.string.dashboard_chart_fullscreen_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimensions.spacingM))
                if (points.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.dashboard_chart_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    ) {
                        // Réserve un peu de place sous le Canvas pour la légende du graphique
                        // (DashboardLineChart empile Canvas + légende dans la hauteur donnée).
                        val chartHeight = (maxHeight - 32.dp).coerceAtLeast(120.dp)
                        DashboardLineChart(
                            points = points,
                            mode = mode,
                            height = chartHeight,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ─── Couleur "Nouveaux clients" (vert success accessible, distinct de primary) ──

private val colorNewCustomers = Color(0xFF2E7D32)

// ─── Graphique double axe (commandes + CA) ───────────────────────────────────

// Marges internes du canvas
private val chartPadLeft = 52.dp
private val chartPadRight = 60.dp
private val chartPadRightNoAxis = 12.dp
private val chartPadTop = 12.dp
private val chartPadBottom = 28.dp

/** Arrondit rawMax à la prochaine valeur "ronde" supérieure (5, 10, 25, 50…). */
private fun niceMaxInt(rawMax: Int): Int {
    if (rawMax <= 0) return 5
    val magnitude =
        when {
            rawMax <= 20 -> 5
            rawMax <= 50 -> 10
            rawMax <= 100 -> 25
            rawMax <= 200 -> 50
            rawMax <= 500 -> 100
            rawMax <= 1_000 -> 250
            else -> 500
        }
    return ((rawMax + magnitude - 1) / magnitude) * magnitude
}

/** Arrondit rawMax Double au prochain multiple "rond" supérieur. */
private fun niceMaxDouble(rawMax: Double): Double {
    if (rawMax <= 0.0) return 100.0
    val magnitude =
        when {
            rawMax <= 10.0 -> 5.0
            rawMax <= 50.0 -> 10.0
            rawMax <= 100.0 -> 25.0
            rawMax <= 200.0 -> 50.0
            rawMax <= 500.0 -> 100.0
            rawMax <= 1_000.0 -> 250.0
            rawMax <= 2_000.0 -> 500.0
            rawMax <= 5_000.0 -> 1_000.0
            rawMax <= 10_000.0 -> 2_500.0
            else -> 5_000.0
        }
    return ceil(rawMax / magnitude) * magnitude
}

/** Formate une valeur de CA pour les labels d'axe (ex: 0€, 450€, 1,5k€, 2,0M€). */
private fun formatCaLabel(value: Double): String =
    when {
        value >= 1_000_000.0 -> "${"%.1f".format(value / 1_000_000.0)}M€"
        value >= 1_000.0 -> "${"%.1f".format(value / 1_000.0)}k€"
        else -> "${value.toInt()}€"
    }

/** Construit un Path lissé via Catmull-Rom converti en Bézier cubique. */
private fun catmullRomPath(offsets: List<Offset>): Path {
    val path = Path()
    if (offsets.size < 2) return path
    path.moveTo(offsets[0].x, offsets[0].y)
    val n = offsets.size
    for (i in 0 until n - 1) {
        val p0 = if (i > 0) offsets[i - 1] else offsets[i]
        val p1 = offsets[i]
        val p2 = offsets[i + 1]
        val p3 = if (i + 2 < n) offsets[i + 2] else offsets[i + 1]
        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

@Composable
private fun chartSeriesColor(kind: ChartSeriesKind): Color =
    when (kind) {
        ChartSeriesKind.TURNOVER -> Color(0xFF1976D2)
        ChartSeriesKind.TURNOVER_PREVIOUS -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        ChartSeriesKind.AVG_CART, ChartSeriesKind.ORDERS_PAID -> MaterialTheme.colorScheme.primary
        ChartSeriesKind.ORDERS_FREE -> MaterialTheme.colorScheme.primaryContainer
        ChartSeriesKind.NEW_CUSTOMERS -> colorNewCustomers
    }

@Composable
private fun chartSeriesLabel(kind: ChartSeriesKind): String =
    stringResource(
        id =
            when (kind) {
                ChartSeriesKind.TURNOVER -> R.string.dashboard_legend_turnover
                ChartSeriesKind.TURNOVER_PREVIOUS -> R.string.dashboard_legend_turnover_previous
                ChartSeriesKind.AVG_CART -> R.string.dashboard_legend_avg_cart
                ChartSeriesKind.ORDERS_PAID -> R.string.dashboard_legend_orders_paid
                ChartSeriesKind.ORDERS_FREE -> R.string.dashboard_legend_orders_free
                ChartSeriesKind.NEW_CUSTOMERS -> R.string.dashboard_chart_new_customers
            },
    )

/** Séries en pointillés : comparatif N-1 et nouveaux clients (superposition). */
private fun ChartSeriesKind.isDashed(): Boolean = this == ChartSeriesKind.TURNOVER_PREVIOUS || this == ChartSeriesKind.NEW_CUSTOMERS

/** Max d'axe « rond » pour les séries d'un côté ; 1.0 si aucune série. */
private fun axisMaxFor(series: List<ChartSeriesData>): Double {
    if (series.isEmpty()) return 1.0
    val rawMax = series.maxOf { s -> s.values.maxOrNull() ?: 0.0 }
    return if (series.first().unit == ChartUnit.COUNT) niceMaxInt(ceil(rawMax).toInt()).toDouble() else niceMaxDouble(rawMax)
}

private fun formatAxisValue(
    value: Double,
    unit: ChartUnit,
): String = if (unit == ChartUnit.COUNT) value.roundToInt().toString() else formatCaLabel(value)

/**
 * Graphique en courbes lissées piloté par [mode] (cf. [buildChartSeries]) : jusqu'à deux axes Y
 * (gauche/droite selon les séries), tooltip au tap, légende dynamique.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod", "ComplexMethod")
@Composable
internal fun DashboardLineChart(
    points: List<DashboardChartPoint>,
    mode: DashboardChartMode,
    height: Dp = 200.dp,
    showNewCustomers: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val series = remember(mode, points, showNewCustomers) { buildChartSeries(mode, points, showNewCustomers) }
    val colors = series.map { chartSeriesColor(it.kind) }
    val labels = series.map { chartSeriesLabel(it.kind) }
    val leftSeries = series.filter { it.side == ChartAxisSide.LEFT }
    val rightSeries = series.filter { it.side == ChartAxisSide.RIGHT }
    val maxLeft = remember(series) { axisMaxFor(leftSeries) }
    val maxRight = remember(series) { axisMaxFor(rightSeries) }
    val padRight = if (rightSeries.isNotEmpty()) chartPadRight else chartPadRightNoAxis

    val colorGrid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val colorLabel = MaterialTheme.colorScheme.onSurfaceVariant
    val colorSurface = MaterialTheme.colorScheme.surfaceContainerLowest
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val chartDesc = rememberChartContentDescription(series.first())
    val currencyFormatter = rememberCurrencyFormatter()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    // Animation d'apparition (0 → 1 en 700 ms)
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 700),
        label = "line_chart_anim",
    )
    LaunchedEffect(points) { animProgress = 1f }

    // Sélection interactive au tap
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // Indices à afficher pour les labels X (max 4, espacés uniformément — évite le chevauchement)
    val maxXLabels = 4
    val xLabelIndices: List<Int> =
        if (points.size <= maxXLabels) {
            points.indices.toList()
        } else {
            val step = (points.size - 1).toFloat() / (maxXLabels - 1)
            (0 until maxXLabels)
                .map { i -> (i * step).roundToInt().coerceIn(0, points.size - 1) }
                .distinct()
        }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val totalWidthDp = maxWidth

            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .semantics(mergeDescendants = true) { contentDescription = chartDesc }
                        .pointerInput(points, padRight) {
                            detectTapGestures { offset ->
                                val n = points.size
                                if (n < 2) return@detectTapGestures
                                val pl = chartPadLeft.toPx()
                                val pr = padRight.toPx()
                                val step = (size.width.toFloat() - pl - pr) / (n - 1)
                                val idx = ((offset.x - pl) / step).roundToInt().coerceIn(0, n - 1)
                                selectedIndex = if (selectedIndex == idx) -1 else idx
                            }
                        },
            ) {
                val n = points.size
                if (n < 2) return@Canvas

                val pl = chartPadLeft.toPx()
                val pr = padRight.toPx()
                val pt = chartPadTop.toPx()
                val pb = chartPadBottom.toPx()
                val cw = size.width - pl - pr
                val ch = size.height - pt - pb
                val stepX = if (n > 1) cw / (n - 1) else cw

                fun yFor(
                    value: Double,
                    axisMax: Double,
                ): Float = pt + ch - ch * (value / axisMax).toFloat().coerceIn(0f, 1f) * animatedProgress

                val offsetsBySeries =
                    series.map { s ->
                        val axisMax = if (s.side == ChartAxisSide.LEFT) maxLeft else maxRight
                        s.values.mapIndexed { i, v -> Offset(pl + stepX * i, yFor(v, axisMax)) }
                    }

                // ── Labels axes Y (gauche, puis droit si une série l'utilise) ─────────
                val gridCount = 4
                repeat(gridCount + 1) { i ->
                    val fraction = i.toFloat() / gridCount
                    val y = pt + ch * fraction
                    if (leftSeries.isNotEmpty()) {
                        val measured =
                            textMeasurer.measure(
                                formatAxisValue(maxLeft * (1.0 - fraction), leftSeries.first().unit),
                                style = labelStyle,
                            )
                        drawText(
                            textLayoutResult = measured,
                            color = colorLabel,
                            topLeft = Offset(pl - measured.size.width.toFloat() - 6.dp.toPx(), y - measured.size.height / 2f),
                        )
                    }
                    if (rightSeries.isNotEmpty()) {
                        val measured =
                            textMeasurer.measure(
                                formatAxisValue(maxRight * (1.0 - fraction), rightSeries.first().unit),
                                style = labelStyle,
                            )
                        drawText(
                            textLayoutResult = measured,
                            color = colorLabel,
                            topLeft = Offset(pl + cw + 6.dp.toPx(), y - measured.size.height / 2f),
                        )
                    }
                }

                // ── Grille horizontale pointillée ─────────────────────────────────────
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                repeat(gridCount + 1) { i ->
                    val y = pt + ch * i / gridCount
                    drawLine(
                        color = colorGrid,
                        start = Offset(pl, y),
                        end = Offset(pl + cw, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }

                // ── Courbes lissées Catmull-Rom ───────────────────────────────────────
                series.forEachIndexed { si, s ->
                    drawPath(
                        path = catmullRomPath(offsetsBySeries[si]),
                        color = colors[si],
                        style =
                            Stroke(
                                width = 2.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                pathEffect =
                                    if (s.kind.isDashed()) {
                                        PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 4.dp.toPx()))
                                    } else {
                                        null
                                    },
                            ),
                    )
                }

                // ── Marqueurs sur les vrais points ────────────────────────────────────
                val mr = 4.dp.toPx()
                offsetsBySeries.forEachIndexed { si, offsets ->
                    offsets.forEachIndexed { i, o ->
                        val sel = i == selectedIndex
                        drawCircle(colors[si], if (sel) mr * 1.8f else mr, o)
                        if (sel) drawCircle(Color.White, mr * 0.7f, o)
                    }
                }

                // ── Indicateur vertical de sélection ─────────────────────────────────
                if (selectedIndex in points.indices) {
                    val selX = pl + stepX * selectedIndex
                    drawLine(
                        color = colorLabel.copy(alpha = 0.35f),
                        start = Offset(selX, pt),
                        end = Offset(selX, pt + ch),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                    offsetsBySeries.forEachIndexed { si, offsets ->
                        drawCircle(colors[si].copy(alpha = 0.15f), 12.dp.toPx(), offsets[selectedIndex])
                    }
                }

                // ── Labels X intelligents (max 4, format court dd/MM pour les dates ISO) ──
                xLabelIndices.forEach { idx ->
                    val lbl = formatXAxisLabel(points[idx].label)
                    val measured = textMeasurer.measure(lbl, style = labelStyle)
                    val xPos = pl + stepX * idx
                    drawText(
                        textLayoutResult = measured,
                        color = colorLabel,
                        topLeft =
                            Offset(
                                x =
                                    (xPos - measured.size.width / 2f)
                                        .coerceIn(pl, pl + cw - measured.size.width),
                                y = pt + ch + 4.dp.toPx(),
                            ),
                    )
                }
            }

            // ── Tooltip overlay Compose ───────────────────────────────────────────────
            if (selectedIndex in points.indices) {
                val n = points.size
                val usableW = totalWidthDp - chartPadLeft - padRight
                val stepXDp = if (n > 1) usableW / (n - 1) else usableW
                val rawX = chartPadLeft + stepXDp * selectedIndex
                val tooltipW = 170.dp
                val clampedX = rawX.coerceIn(0.dp, (totalWidthDp - tooltipW).coerceAtLeast(0.dp))

                ChartTooltipBubble(
                    modifier =
                        Modifier
                            .offset(x = clampedX, y = chartPadTop + 8.dp)
                            .width(tooltipW),
                    label = points[selectedIndex].label,
                    rows =
                        series.mapIndexed { si, s ->
                            val v = s.values[selectedIndex]
                            val text = if (s.unit == ChartUnit.COUNT) v.roundToInt().toString() else currencyFormatter.format(v)
                            ChartTooltipRow(colors[si], "${labels[si]} : $text")
                        },
                    containerColor = colorSurface,
                    onContainerColor = colorOnSurface,
                )
            }
        }

        // ── Légende sous le graphe ────────────────────────────────────────────────────
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            series.forEachIndexed { si, _ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors[si]))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = labels[si],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private data class ChartTooltipRow(
    val color: Color,
    val text: String,
)

@Composable
private fun ChartTooltipBubble(
    label: String,
    rows: List<ChartTooltipRow>,
    containerColor: Color,
    onContainerColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainerColor,
            )
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(row.color))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = row.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainerColor,
                    )
                }
            }
        }
    }
}

// ─── Bannière erreur inline ───────────────────────────────────────────────────

@Composable
private fun ErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = Dimensions.spacingS)
                .clip(RoundedCornerShape(Dimensions.chipCornerRadius)),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(Dimensions.chipCornerRadius),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimensions.spacingM, vertical = Dimensions.spacingS),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingS))
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(id = R.string.action_retry),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun rememberChartContentDescription(series: ChartSeriesData): String {
    val currencyFormatter = rememberCurrencyFormatter()
    val numberFormatter = rememberNumberFormatter()
    val peakValue = series.values.maxOrNull() ?: 0.0
    val formattedPeak =
        remember(peakValue, series.unit) {
            if (series.unit == ChartUnit.MONEY) currencyFormatter.format(peakValue) else numberFormatter.format(peakValue)
        }
    val trendRes =
        remember(series) {
            val first = series.values.firstOrNull() ?: 0.0
            val last = series.values.lastOrNull() ?: 0.0
            when {
                last > first * 1.001 -> R.string.dashboard_chart_trend_rising
                last < first * 0.999 -> R.string.dashboard_chart_trend_falling
                else -> R.string.dashboard_chart_trend_stable
            }
        }
    val trendLabel = stringResource(id = trendRes)
    return stringResource(id = R.string.dashboard_chart_content_description, formattedPeak, trendLabel)
}

@Composable
internal fun rememberCurrencyFormatter(): NumberFormat {
    val locales = LocalConfiguration.current.locales
    val locale = if (!locales.isEmpty) locales[0] else java.util.Locale.getDefault()
    return remember(locale) { euroCurrencyFormatter(locale) }
}

@Composable
internal fun rememberNumberFormatter(): NumberFormat {
    val locales = LocalConfiguration.current.locales
    val locale = if (!locales.isEmpty) locales[0] else java.util.Locale.getDefault()
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

private fun formatLastUpdated(isoString: String): String =
    runCatching {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(zoned)
    }.getOrElse { isoString }

/**
 * Formate une plage de dates libre pour l'affichage dans l'en-tête.
 * Ex : "12 mai – 18 juin 2026" (année une fois si même année, sinon sur les deux). Locale système.
 */
private fun formatCustomRangeDisplay(
    from: String,
    to: String,
): String =
    runCatching {
        val fromDate = LocalDate.parse(from)
        val toDate = LocalDate.parse(to)
        val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val fromFmt =
            if (fromDate.year == toDate.year) fromDate.format(dayMonth) else fromDate.format(dayMonthYear)
        "$fromFmt – ${toDate.format(dayMonthYear)}"
    }.getOrElse { "$from – $to" }

/**
 * Formate un label d'axe X pour l'affichage sous le graphe.
 *
 * Trois cas couverts :
 * - `"yyyy-MM-dd HH:mm:ss"` (horodatage horaire, période « Aujourd'hui ») → `"HH'h'"` (ex : `"15h"`).
 * - `"yyyy-MM-dd"` (label journalier) → `"dd/MM"` (ex : `"26/06"`).
 * - Preset non parseable (`"Lun"`, `"S1"`, `"Semaine 1"`…) → retourné tel quel.
 */
internal fun formatXAxisLabel(raw: String): String =
    runCatching {
        // Cas 1 : horodatage "yyyy-MM-dd HH:mm:ss" — labels horaires de la période Aujourd'hui
        val dt = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        dt.format(DateTimeFormatter.ofPattern("HH'h'"))
    }.getOrElse {
        runCatching {
            // Cas 2 : date ISO "yyyy-MM-dd" — labels journaliers
            LocalDate.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM"))
        }.getOrElse { raw } // Cas 3 : preset non parseable — retourné brut
    }

// ─── Helpers ─────────────────────────────────────────────────────────────────

/** Retourne le string resource correspondant au libellé "vs même période l'an dernier" selon la période active. */
@StringRes
private fun periodComparisonLabelRes(period: DashboardPeriod): Int =
    when (period) {
        DashboardPeriod.TODAY -> R.string.dashboard_chart_vs_yesterday
        DashboardPeriod.WEEK -> R.string.dashboard_chart_vs_prev_week
        DashboardPeriod.MONTH -> R.string.dashboard_chart_vs_prev_month
        DashboardPeriod.QUARTER -> R.string.dashboard_chart_vs_prev_quarter
        DashboardPeriod.YEAR -> R.string.dashboard_chart_vs_prev_year
    }

// ─── Calcul de tendance KPI (fonctions pures, testables) ──────────────────────

/** Seuil (en %) au-delà duquel un pourcentage de variation est jugé non informatif (> 10x). */
internal const val KPI_TREND_HUGE_THRESHOLD_PERCENT = 1000.0

/** Tendance d'un KPI, assainie : jamais de pourcentage sur une base nulle ni de nombre délirant. */
internal sealed interface KpiTrend {
    val isPositive: Boolean

    /** Variation normale en % (signée). */
    data class Percent(val value: Double) : KpiTrend {
        override val isPositive: Boolean get() = value >= 0.0
    }

    /** Base de comparaison nulle : croissance non définie (badge « nouv. »). */
    data object New : KpiTrend {
        override val isPositive: Boolean get() = true
    }

    /** Hausse d'au moins [KPI_TREND_HUGE_THRESHOLD_PERCENT] % : pas de nombre trompeur (badge « ≥ +1000 % »). */
    data object Huge : KpiTrend {
        override val isPositive: Boolean get() = true
    }
}

/**
 * Règle commune N-1 :
 * - base [previous] <= 0 : [KpiTrend.New] si [current] > 0, sinon `null` (rien à comparer) ;
 * - |variation| >= 1000 % : [KpiTrend.Huge] (hausse) ou [KpiTrend.Percent] (une baisse ne dépasse jamais -100 %) ;
 * - sinon : [KpiTrend.Percent].
 */
internal fun classifyTrend(
    current: Double,
    previous: Double,
): KpiTrend? {
    if (previous <= 0.0) return if (current > 0.0) KpiTrend.New else null
    val percent = (current - previous) / previous * 100.0
    return if (percent >= KPI_TREND_HUGE_THRESHOLD_PERCENT) KpiTrend.Huge else KpiTrend.Percent(percent)
}

/**
 * Variation entre [current] et [previous] (ex : CA vs même période l'an dernier, N-1),
 * assainie par [classifyTrend].
 *
 * Retourne `null` (badge masqué) si [isCustomRange] est vrai (pas de "précédent" comparable
 * en plage libre), si [previous] est absent, ou si base et valeur courante sont nulles.
 */
internal fun kpiTrendPercent(
    current: Double,
    previous: Double?,
    isCustomRange: Boolean,
): KpiTrend? {
    if (isCustomRange || previous == null) return null
    return classifyTrend(current, previous)
}

/**
 * Tendance "sur la période" dérivée d'une série de buckets [values] (ex : commandes,
 * nouveaux clients, panier moyen par jour/semaine) : compare la somme de la 2ᵉ moitié
 * de la série à la somme de la 1ʳᵉ moitié, avec la même règle d'assainissement que [classifyTrend].
 *
 * Retourne `null` (badge masqué) si [isCustomRange] est vrai, si la série a moins de
 * 2 points, ou si les deux moitiés sont nulles.
 */
internal fun kpiTrendPercentFromSeries(
    values: List<Double>,
    isCustomRange: Boolean,
): KpiTrend? {
    if (isCustomRange || values.size < 2) return null
    val mid = values.size / 2
    return classifyTrend(current = values.drop(mid).sum(), previous = values.take(mid).sum())
}

// ─── Modèle local ─────────────────────────────────────────────────────────────

private const val LOW_STOCK_CAP = 50

/** Statut d'affichage d'une tuile à données réseau. */
private fun <T> WidgetLoad<T>.tileStatus(): TileStatus =
    when (this) {
        is WidgetLoad.Data -> TileStatus.Ready
        WidgetLoad.Error -> TileStatus.Unavailable
        else -> TileStatus.Loading
    }

/** Valeur d'une tuile KPI réseau : texte formaté, ou texte annoncé (chargement / indisponible). */
private fun <T> widgetValueText(
    state: WidgetLoad<T>,
    loadingText: String,
    unavailableText: String,
    format: (T) -> String,
): String =
    when (state) {
        is WidgetLoad.Data -> format(state.value)
        WidgetLoad.Error -> unavailableText
        else -> loadingText
    }

private data class KpiItem(
    val title: String,
    val value: String,
    val icon: ImageVector,
    /** Callback de navigation. Null = carte non cliquable. */
    val onClick: (() -> Unit)? = null,
    /** Sous-texte discret sous la valeur (ex : « dont 3 offertes »). Null = rien. */
    val subtitle: String? = null,
    /** Tendance vs même période l'an dernier (CA) / 1ʳᵉ moitié de la série (autres KPI). Null = badge masqué. */
    val trend: KpiTrend? = null,
    /** Série pour la mini-sparkline. Peut être vide/trop courte/plate — géré par [KpiSparkline]. */
    val sparklineValues: List<Float> = emptyList(),
    /** Chargement / indisponible : [value] porte alors le texte annoncé, la tuile affiche un état dédié. */
    val status: TileStatus = TileStatus.Ready,
)

// ─── Previews ─────────────────────────────────────────────────────────────────

/**
 * Preuve visuelle du fix fontScale : en-tête boutique, cartes KPI et en-tête du graphique
 * (montant en headlineMedium) ne doivent pas déborder hors de l'écran à fontScale 1.5 puis 2.0.
 */
@Preview(showBackground = true, name = "Dashboard — contenu")
@Preview(showBackground = true, fontScale = 1.5f, name = "Dashboard — contenu — fontScale 1.5")
@Preview(showBackground = true, fontScale = 2f, name = "Dashboard — contenu — fontScale 2.0")
@Composable
private fun PreviewDashboardContent() {
    PrestaFlowTheme {
        DashboardScreen(
            state =
                DashboardUiState(
                    selectedPeriod = DashboardPeriod.TODAY,
                    snapshot =
                        DashboardSnapshot(
                            period = DashboardPeriod.TODAY,
                            turnover = 1240.50,
                            ordersCount = 38,
                            customersCount = 9,
                            productsCount = 142,
                            chart =
                                listOf(
                                    DashboardChartPoint(label = "Lun", orders = 12, customers = 3, turnover = 400.0, newCustomers = 2),
                                    DashboardChartPoint(label = "Mar", orders = 20, customers = 5, turnover = 700.0, newCustomers = 4),
                                    DashboardChartPoint(label = "Mer", orders = 16, customers = 4, turnover = 550.0, newCustomers = 1),
                                    DashboardChartPoint(label = "Jeu", orders = 26, customers = 7, turnover = 900.0, newCustomers = 6),
                                    DashboardChartPoint(label = "Ven", orders = 19, customers = 5, turnover = 650.0, newCustomers = 3),
                                    DashboardChartPoint(label = "Sam", orders = 31, customers = 8, turnover = 1100.0, newCustomers = 5),
                                    DashboardChartPoint(label = "Dim", orders = 38, customers = 9, turnover = 1240.5, newCustomers = 7),
                                ),
                            lastUpdatedIso = "2026-06-19T10:30:00Z",
                        ),
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                ),
            onPeriodSelected = {},
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, name = "Dashboard — plage libre")
@Composable
private fun PreviewDashboardCustomRange() {
    PrestaFlowTheme {
        DashboardScreen(
            state =
                DashboardUiState(
                    selectedPeriod = DashboardPeriod.WEEK,
                    customRange = Pair("2026-05-12", "2026-06-18"),
                    snapshot =
                        DashboardSnapshot(
                            period = DashboardPeriod.TODAY,
                            turnover = 3580.0,
                            ordersCount = 92,
                            customersCount = 24,
                            productsCount = 142,
                            chart =
                                listOf(
                                    DashboardChartPoint(label = "S1", orders = 12, customers = 3, turnover = 400.0, newCustomers = 2),
                                    DashboardChartPoint(label = "S2", orders = 20, customers = 5, turnover = 700.0, newCustomers = 8),
                                    DashboardChartPoint(label = "S3", orders = 26, customers = 7, turnover = 900.0, newCustomers = 6),
                                    DashboardChartPoint(label = "S4", orders = 34, customers = 9, turnover = 1580.0, newCustomers = 8),
                                ),
                            lastUpdatedIso = "2026-06-19T10:30:00Z",
                        ),
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                ),
            onPeriodSelected = {},
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, name = "Dashboard — chargement")
@Composable
private fun PreviewDashboardLoading() {
    PrestaFlowTheme {
        DashboardScreen(
            state = DashboardUiState(isLoading = true, snapshot = null),
            onPeriodSelected = {},
            onRefresh = {},
        )
    }
}

@Preview(showBackground = true, name = "Dashboard — erreur")
@Composable
private fun PreviewDashboardError() {
    PrestaFlowTheme {
        DashboardScreen(
            state = DashboardUiState(isLoading = false, snapshot = null),
            onPeriodSelected = {},
            onRefresh = {},
        )
    }
}
