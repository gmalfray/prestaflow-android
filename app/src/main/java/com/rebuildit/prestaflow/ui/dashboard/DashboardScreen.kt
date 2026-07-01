package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DashboardRoute(
    onAddShop: () -> Unit = {},
    onOrdersClick: (DashboardPeriod) -> Unit = {},
    onClientsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()
    DashboardScreen(
        modifier = modifier.fillMaxSize(),
        state = state,
        connections = connections,
        onPeriodSelected = viewModel::onPeriodSelected,
        onCustomRangeSelected = viewModel::onCustomRangeSelected,
        onRefresh = viewModel::onRefresh,
        onSwitchShop = shopsViewModel::switchShop,
        onAddShop = onAddShop,
        onOrdersClick = { onOrdersClick(state.selectedPeriod) },
        onClientsClick = onClientsClick,
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
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
    onOrdersClick: () -> Unit = {},
    onClientsClick: () -> Unit = {},
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
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
                onOrdersClick = onOrdersClick,
                onClientsClick = onClientsClick,
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
@Suppress("LongParameterList", "LongMethod")
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
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    onOrdersClick: () -> Unit = {},
    onClientsClick: () -> Unit = {},
) {
    val currencyFormatter = rememberCurrencyFormatter()
    val numberFormatter = rememberNumberFormatter()
    val turnoverText = remember(snapshot.turnover) { currencyFormatter.format(snapshot.turnover) }
    val ordersText = remember(snapshot.ordersCount) { numberFormatter.format(snapshot.ordersCount) }
    // Nouveaux clients = somme de newCustomers sur les points du graphe.
    // Cohérent avec la série "Nouveaux clients" de DualAxisSalesChart.
    // Vaut 0 tant que le connecteur < v1.7 ne remplit pas ce champ.
    val newCustomersTotal = remember(snapshot.chart) { snapshot.chart.sumOf { it.newCustomers } }
    val newCustomersTotalText = remember(newCustomersTotal) { numberFormatter.format(newCustomersTotal) }
    val avgCart =
        remember(snapshot.turnover, snapshot.ordersCount) {
            if (snapshot.ordersCount > 0) snapshot.turnover / snapshot.ordersCount else 0.0
        }
    val avgCartText = remember(avgCart) { currencyFormatter.format(avgCart) }
    val lastUpdatedText = remember(snapshot.lastUpdatedIso) { formatLastUpdated(snapshot.lastUpdatedIso) }

    PullToRefreshBox(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // En-tête collant intégré dans la liste pour éviter un ScaffoldTopBar
            item {
                DashboardHeader(
                    connections = connections,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    selectedPeriod = selectedPeriod,
                    customRange = customRange,
                    onPeriodSelected = onPeriodSelected,
                    onCustomRangeSelected = onCustomRangeSelected,
                    onSwitchShop = onSwitchShop,
                    onAddShop = onAddShop,
                )
            }

            // Bandeau erreur inline
            if (errorMessage != null) {
                item {
                    ErrorBanner(
                        message = errorMessage,
                        onRetry = onRefresh,
                        modifier = Modifier.padding(horizontal = Dimensions.screenEdgeMargin),
                    )
                }
            }

            // Grille KPI 2x2
            item {
                DashboardKpiGrid(
                    modifier =
                        Modifier
                            .padding(horizontal = Dimensions.screenEdgeMargin)
                            .padding(top = Dimensions.spacingL),
                    kpiItems =
                        listOf(
                            KpiItem(
                                title = stringResource(id = R.string.dashboard_kpi_turnover),
                                value = turnoverText,
                                icon = Icons.Outlined.Payments,
                            ),
                            KpiItem(
                                title = stringResource(id = R.string.dashboard_kpi_orders),
                                value = ordersText,
                                icon = Icons.Outlined.ShoppingBag,
                                onClick = onOrdersClick,
                            ),
                            KpiItem(
                                title = stringResource(id = R.string.dashboard_kpi_avg_cart),
                                value = avgCartText,
                                icon = Icons.Outlined.Analytics,
                            ),
                            KpiItem(
                                title = stringResource(id = R.string.dashboard_kpi_customers),
                                value = newCustomersTotalText,
                                icon = Icons.Outlined.Group,
                                onClick = onClientsClick,
                            ),
                        ),
                )
            }

            // Carte graphique CA
            item {
                DashboardChartCard(
                    modifier =
                        Modifier
                            .padding(horizontal = Dimensions.screenEdgeMargin)
                            .padding(top = Dimensions.spacingL),
                    points = snapshot.chart,
                    totalText = turnoverText,
                    currentTurnover = snapshot.turnover,
                    previousTurnover = snapshot.previousTurnover,
                    // En mode plage libre, on n'affiche pas le comparatif "vs période précédente"
                    selectedPeriod = if (customRange == null) selectedPeriod else null,
                )
            }

            // Dernière synchronisation
            item {
                Text(
                    modifier =
                        Modifier
                            .padding(horizontal = Dimensions.screenEdgeMargin)
                            .padding(top = Dimensions.spacingS, bottom = Dimensions.spacingL),
                    text = stringResource(id = R.string.label_last_sync, lastUpdatedText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── En-tête ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardHeader(
    connections: List<ShopConnection>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    selectedPeriod: DashboardPeriod,
    customRange: Pair<String, String>?,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onCustomRangeSelected: (String, String) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshDesc = stringResource(id = R.string.dashboard_content_description_refresh)
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
                        val endMs = datePickerState.selectedEndDateMillis
                        if (startMs != null && endMs != null) {
                            val from = LocalDate.ofInstant(
                                Instant.ofEpochMilli(startMs),
                                ZoneOffset.UTC,
                            ).toString()
                            val to = LocalDate.ofInstant(
                                Instant.ofEpochMilli(endMs),
                                ZoneOffset.UTC,
                            ).toString()
                            onCustomRangeSelected(from, to)
                        }
                        showDatePicker = false
                    },
                    // Désactivé tant que la plage n'est pas complète (start ET end requis)
                    enabled = datePickerState.selectedStartDateMillis != null &&
                        datePickerState.selectedEndDateMillis != null,
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
                    val subtitleText = if (customRange != null) {
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
                Box(
                    modifier =
                        Modifier
                            .size(Dimensions.minTouchTarget)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .semantics { contentDescription = refreshDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier.size(Dimensions.minTouchTarget),
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Dimensions.iconSizeMedium),
                            )
                        }
                    }
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

@Composable
private fun DashboardKpiGrid(
    kpiItems: List<KpiItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimensions.gutter),
    ) {
        kpiItems.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimensions.gutter),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowItems.forEach { item ->
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        item = item,
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Carte KPI Terracotta — fond blanc, icône dans un conteneur teinté,
 * label en haut uppercase, valeur en bas (title-lg Stitch).
 * Rayon 20dp signature du design system.
 * Cliquable si [item.onClick] est non nul (navigation vers la liste filtrée).
 */
@Composable
private fun KpiCard(
    item: KpiItem,
    modifier: Modifier = Modifier,
) {
    val cardDesc = stringResource(id = R.string.dashboard_content_description_kpi_card, item.title, item.value)
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
                        contentDescription = cardDesc
                        if (item.onClick != null) role = Role.Button
                    },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Icône dans un conteneur teinté
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

            // Label + valeur
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (item.onClick != null) {
        Card(
            onClick = item.onClick,
            modifier = modifier.aspectRatio(1f),
            shape = cardShape,
            colors = cardColors,
            elevation = cardElevation,
        ) { innerContent() }
    } else {
        Card(
            modifier = modifier.aspectRatio(1f),
            shape = cardShape,
            colors = cardColors,
            elevation = cardElevation,
        ) { innerContent() }
    }
}

// ─── Carte graphique ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardChartCard(
    points: List<DashboardChartPoint>,
    totalText: String,
    currentTurnover: Double,
    previousTurnover: Double?,
    /**
     * Null en mode plage libre : le comparatif "vs période précédente" n'est alors pas affiché.
     */
    selectedPeriod: DashboardPeriod?,
    modifier: Modifier = Modifier,
) {
    // Toggle 3ᵉ série "Nouveaux clients" — local à la carte
    var showNewCustomers by remember { mutableStateOf(false) }

    Card(
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
            // En-tête : titre + tendance à gauche, total à droite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(id = R.string.dashboard_chart_title_turnover),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // Comparatif vs période précédente — masqué en mode plage libre
                    if (previousTurnover != null && selectedPeriod != null) {
                        val delta =
                            if (previousTurnover > 0.0) {
                                (currentTurnover - previousTurnover) / previousTurnover * 100.0
                            } else {
                                0.0
                            }
                        val isPositive = delta >= 0.0
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${if (isPositive) "+" else ""}${"%.1f".format(delta)}%",
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (isPositive) {
                                        Color(0xFF2E7D32)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                            )
                            Text(
                                text = stringResource(id = periodComparisonLabelRes(selectedPeriod)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
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

            // Toggle 3ᵉ série : chip compact "Nouveaux clients"
            if (points.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FilterChip(
                        selected = showNewCustomers,
                        onClick = { showNewCustomers = !showNewCustomers },
                        label = {
                            Text(
                                text = stringResource(id = R.string.dashboard_chart_new_customers),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorNewCustomers.copy(alpha = 0.15f),
                            selectedLabelColor = colorNewCustomers,
                        ),
                    )
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
                DualAxisSalesChart(
                    points = points,
                    height = 160.dp,
                    showNewCustomers = showNewCustomers,
                )
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
private val chartPadTop = 12.dp
private val chartPadBottom = 28.dp

/** Arrondit rawMax à la prochaine valeur "ronde" supérieure (5, 10, 25, 50…). */
private fun niceMaxInt(rawMax: Int): Int {
    if (rawMax <= 0) return 5
    val magnitude = when {
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
    val magnitude = when {
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
private fun formatCaLabel(value: Double): String = when {
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

@Suppress("LongMethod", "ComplexMethod")
@Composable
internal fun DualAxisSalesChart(
    points: List<DashboardChartPoint>,
    height: Dp = 200.dp,
    showNewCustomers: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    val colorOrders = MaterialTheme.colorScheme.primary
    val colorTurnover = Color(0xFF1976D2)
    val colorNewCust = colorNewCustomers
    val colorGrid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val colorLabel = MaterialTheme.colorScheme.onSurfaceVariant
    val colorSurface = MaterialTheme.colorScheme.surfaceContainerLowest
    val colorOnSurface = MaterialTheme.colorScheme.onSurface
    val chartDesc = rememberChartContentDescription(points)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    // Axe gauche = max(orders, newCustomers si série active)
    val rawMaxLeft = if (showNewCustomers) {
        max(points.maxOf { it.orders }, points.maxOf { it.newCustomers })
    } else {
        points.maxOf { it.orders }
    }
    val maxOrders = niceMaxInt(rawMaxLeft)
    val maxTurnover = niceMaxDouble(points.maxOf { it.turnover })

    // Animation d'apparition (0 → 1 en 700 ms)
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 700),
        label = "dual_chart_anim",
    )
    LaunchedEffect(points) { animProgress = 1f }

    // Sélection interactive au tap
    var selectedIndex by remember { mutableIntStateOf(-1) }

    // Indices à afficher pour les labels X (max 4, espacés uniformément — évite le chevauchement)
    val maxXLabels = 4
    val xLabelIndices: List<Int> = if (points.size <= maxXLabels) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .semantics(mergeDescendants = true) { contentDescription = chartDesc }
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val n = points.size
                            if (n < 2) return@detectTapGestures
                            val pl = chartPadLeft.toPx()
                            val pr = chartPadRight.toPx()
                            val step = (size.width.toFloat() - pl - pr) / (n - 1)
                            val idx = ((offset.x - pl) / step).roundToInt().coerceIn(0, n - 1)
                            selectedIndex = if (selectedIndex == idx) -1 else idx
                        }
                    },
            ) {
                val n = points.size
                if (n < 2) return@Canvas

                val pl = chartPadLeft.toPx()
                val pr = chartPadRight.toPx()
                val pt = chartPadTop.toPx()
                val pb = chartPadBottom.toPx()
                val cw = size.width - pl - pr
                val ch = size.height - pt - pb
                val stepX = if (n > 1) cw / (n - 1) else cw

                fun ordY(v: Int): Float =
                    pt + ch - ch * (v.toFloat() / maxOrders).coerceIn(0f, 1f) * animatedProgress

                fun caY(v: Double): Float =
                    pt + ch - ch * (v.toFloat() / maxTurnover.toFloat()).coerceIn(0f, 1f) * animatedProgress

                val ordOffsets = points.mapIndexed { i, p -> Offset(pl + stepX * i, ordY(p.orders)) }
                val caOffsets = points.mapIndexed { i, p -> Offset(pl + stepX * i, caY(p.turnover)) }
                val newCustOffsets = if (showNewCustomers) {
                    points.mapIndexed { i, p -> Offset(pl + stepX * i, ordY(p.newCustomers)) }
                } else {
                    emptyList()
                }

                // ── Labels axe Y gauche (commandes / nouveaux clients) ────────────────
                val gridCount = 4
                repeat(gridCount + 1) { i ->
                    val fraction = i.toFloat() / gridCount
                    val value = (maxOrders * (1f - fraction)).roundToInt()
                    val measured = textMeasurer.measure(value.toString(), style = labelStyle)
                    val y = pt + ch * fraction
                    drawText(
                        textLayoutResult = measured,
                        color = colorLabel,
                        topLeft = Offset(
                            x = pl - measured.size.width.toFloat() - 6.dp.toPx(),
                            y = y - measured.size.height / 2f,
                        ),
                    )
                }

                // ── Labels axe Y droit (CA) ───────────────────────────────────────────
                repeat(gridCount + 1) { i ->
                    val fraction = i.toFloat() / gridCount
                    val value = maxTurnover * (1.0 - fraction)
                    val label = formatCaLabel(value)
                    val measured = textMeasurer.measure(label, style = labelStyle)
                    val y = pt + ch * fraction
                    drawText(
                        textLayoutResult = measured,
                        color = colorLabel,
                        topLeft = Offset(
                            x = pl + cw + 6.dp.toPx(),
                            y = y - measured.size.height / 2f,
                        ),
                    )
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
                drawPath(
                    path = catmullRomPath(ordOffsets),
                    color = colorOrders,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    path = catmullRomPath(caOffsets),
                    color = colorTurnover,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
                if (showNewCustomers && newCustOffsets.isNotEmpty()) {
                    drawPath(
                        path = catmullRomPath(newCustOffsets),
                        color = colorNewCust,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(8.dp.toPx(), 4.dp.toPx()),
                            ),
                        ),
                    )
                }

                // ── Marqueurs sur les vrais points ────────────────────────────────────
                val mr = 4.dp.toPx()
                ordOffsets.forEachIndexed { i, o ->
                    val sel = i == selectedIndex
                    drawCircle(colorOrders, if (sel) mr * 1.8f else mr, o)
                    if (sel) drawCircle(Color.White, mr * 0.7f, o)
                }
                caOffsets.forEachIndexed { i, o ->
                    val sel = i == selectedIndex
                    drawCircle(colorTurnover, if (sel) mr * 1.8f else mr, o)
                    if (sel) drawCircle(Color.White, mr * 0.7f, o)
                }
                if (showNewCustomers) {
                    newCustOffsets.forEachIndexed { i, o ->
                        val sel = i == selectedIndex
                        drawCircle(colorNewCust, if (sel) mr * 1.8f else mr, o)
                        if (sel) drawCircle(Color.White, mr * 0.7f, o)
                    }
                }

                // ── Indicateur vertical de sélection ─────────────────────────────────
                if (selectedIndex in points.indices) {
                    val selX = ordOffsets[selectedIndex].x
                    drawLine(
                        color = colorLabel.copy(alpha = 0.35f),
                        start = Offset(selX, pt),
                        end = Offset(selX, pt + ch),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    )
                    drawCircle(colorOrders.copy(alpha = 0.15f), 12.dp.toPx(), ordOffsets[selectedIndex])
                    drawCircle(colorTurnover.copy(alpha = 0.15f), 12.dp.toPx(), caOffsets[selectedIndex])
                    if (showNewCustomers && newCustOffsets.isNotEmpty()) {
                        drawCircle(colorNewCust.copy(alpha = 0.15f), 12.dp.toPx(), newCustOffsets[selectedIndex])
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
                        topLeft = Offset(
                            x = (xPos - measured.size.width / 2f)
                                .coerceIn(pl, pl + cw - measured.size.width),
                            y = pt + ch + 4.dp.toPx(),
                        ),
                    )
                }
            }

            // ── Tooltip overlay Compose ───────────────────────────────────────────────
            if (selectedIndex in points.indices) {
                val selectedPt = points[selectedIndex]
                val n = points.size
                val usableW = totalWidthDp - chartPadLeft - chartPadRight
                val stepXDp = if (n > 1) usableW / (n - 1) else usableW
                val rawX = chartPadLeft + stepXDp * selectedIndex
                val tooltipW = 160.dp
                val clampedX = rawX.coerceIn(0.dp, totalWidthDp - tooltipW)

                ChartTooltipBubble(
                    modifier = Modifier
                        .offset(x = clampedX, y = chartPadTop + 8.dp)
                        .width(tooltipW),
                    label = selectedPt.label,
                    orders = selectedPt.orders,
                    turnover = selectedPt.turnover,
                    newCustomers = if (showNewCustomers) selectedPt.newCustomers else null,
                    colorOrders = colorOrders,
                    colorTurnover = colorTurnover,
                    colorNewCustomers = colorNewCust,
                    containerColor = colorSurface,
                    onContainerColor = colorOnSurface,
                )
            }
        }

        // ── Légende sous le graphe ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorOrders))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(id = R.string.dashboard_legend_orders),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Dimensions.spacingM))
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorTurnover))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(id = R.string.dashboard_legend_turnover),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showNewCustomers) {
                Spacer(modifier = Modifier.width(Dimensions.spacingM))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colorNewCustomers))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.dashboard_chart_new_customers),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChartTooltipBubble(
    label: String,
    orders: Int,
    turnover: Double,
    newCustomers: Int?,
    colorOrders: Color,
    colorTurnover: Color,
    colorNewCustomers: Color,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorOrders))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.dashboard_tooltip_orders, orders),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerColor,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorTurnover))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(id = R.string.dashboard_tooltip_turnover, formatCaLabel(turnover)),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerColor,
                )
            }
            if (newCustomers != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colorNewCustomers))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.dashboard_tooltip_new_customers, newCustomers),
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
private fun rememberChartContentDescription(points: List<DashboardChartPoint>): String {
    val currencyFormatter = rememberCurrencyFormatter()
    val peakValue = points.maxOf { it.turnover }
    val formattedPeak = remember(peakValue) { currencyFormatter.format(peakValue) }
    val trendRes =
        remember(points) {
            val first = points.first().turnover
            val last = points.last().turnover
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
private fun rememberCurrencyFormatter(): NumberFormat {
    val locales = LocalConfiguration.current.locales
    val locale = if (!locales.isEmpty) locales[0] else java.util.Locale.getDefault()
    return remember(locale) { NumberFormat.getCurrencyInstance(locale) }
}

@Composable
private fun rememberNumberFormatter(): NumberFormat {
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
 * Ex : "12 mai – 18 juin" (locale système).
 */
private fun formatCustomRangeDisplay(from: String, to: String): String =
    runCatching {
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val fromFmt = LocalDate.parse(from).format(fmt)
        val toFmt = LocalDate.parse(to).format(fmt)
        "$fromFmt – $toFmt"
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

/** Retourne le string resource correspondant au libellé "vs période précédente" selon la période active. */
@StringRes
private fun periodComparisonLabelRes(period: DashboardPeriod): Int =
    when (period) {
        DashboardPeriod.TODAY -> R.string.dashboard_chart_vs_yesterday
        DashboardPeriod.WEEK -> R.string.dashboard_chart_vs_prev_week
        DashboardPeriod.MONTH -> R.string.dashboard_chart_vs_prev_month
        DashboardPeriod.QUARTER -> R.string.dashboard_chart_vs_prev_quarter
        DashboardPeriod.YEAR -> R.string.dashboard_chart_vs_prev_year
    }

// ─── Modèle local ─────────────────────────────────────────────────────────────

private data class KpiItem(
    val title: String,
    val value: String,
    val icon: ImageVector,
    /** Callback de navigation. Null = carte non cliquable. */
    val onClick: (() -> Unit)? = null,
)

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Dashboard — contenu")
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
