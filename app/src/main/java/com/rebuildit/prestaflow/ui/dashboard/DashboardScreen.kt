package com.rebuildit.prestaflow.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
                isRefreshing = state.isRefreshing,
                errorMessage = errorMessage,
                connections = connections,
                onPeriodSelected = onPeriodSelected,
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
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onPeriodSelected: (DashboardPeriod) -> Unit,
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
    val customersText =
        remember(snapshot.customersCount) { numberFormatter.format(snapshot.customersCount) }
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
                    onPeriodSelected = onPeriodSelected,
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
                                value = customersText,
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
                    selectedPeriod = selectedPeriod,
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

@Composable
private fun DashboardHeader(
    connections: List<ShopConnection>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    selectedPeriod: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
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
                    Text(
                        text = shopName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

            // Sélecteur de période : chips pill horizontaux
            PeriodChipRow(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
            )
        }
    }
}

// ─── Chips période ────────────────────────────────────────────────────────────

@Composable
private fun PeriodChipRow(
    selectedPeriod: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
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
            val isSelected = period == selectedPeriod
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

@Composable
private fun DashboardChartCard(
    points: List<DashboardChartPoint>,
    totalText: String,
    currentTurnover: Double,
    previousTurnover: Double?,
    selectedPeriod: DashboardPeriod,
    modifier: Modifier = Modifier,
) {
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
                    // Comparatif vs période précédente — affiché uniquement si le connecteur
                    // fournit le CA précédent (champ previous_turnover, disponible dès v1.5.x).
                    if (previousTurnover != null) {
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
                                        Color(0xFF2E7D32) // vert success accessible
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

            // Graphique ou état vide
            if (points.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.dashboard_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TurnoverAreaChart(points = points, height = 160.dp)
            }
        }
    }
}

// ─── Graphique zone ───────────────────────────────────────────────────────────

@Suppress("LongMethod")
@Composable
private fun TurnoverAreaChart(
    points: List<DashboardChartPoint>,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val chartDesc = rememberChartContentDescription(points)
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    // Animation d'entrée : progress de 0 à 1
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(durationMillis = 700),
        label = "chart_anim",
    )
    LaunchedEffect(points) { animProgress = 1f }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(height)
                .semantics(mergeDescendants = true) { contentDescription = chartDesc },
    ) {
        if (points.size < 2) return@Canvas

        val maxValue = points.maxOf { it.turnover }.takeIf { it > 0.0 } ?: 1.0
        val verticalPadding = size.height * 0.08f
        val usableHeight = size.height - (verticalPadding * 2)
        val stepX = size.width / (points.size - 1)

        val allPoints =
            points.mapIndexed { i, p ->
                val normalized = (p.turnover / maxValue).coerceIn(0.0, 1.0)
                val x = stepX * i
                val y = size.height - verticalPadding - (usableHeight * normalized.toFloat() * animatedProgress)
                Offset(x, y)
            }

        // Remplissage dégradé
        val fillPath =
            Path().apply {
                moveTo(allPoints.first().x, size.height)
                allPoints.forEach { lineTo(it.x, it.y) }
                lineTo(allPoints.last().x, size.height)
                close()
            }
        drawPath(
            path = fillPath,
            brush =
                Brush.verticalGradient(
                    colors =
                        listOf(
                            primaryContainer.copy(alpha = 0.35f),
                            primaryContainer.copy(alpha = 0f),
                        ),
                    startY = 0f,
                    endY = size.height,
                ),
        )

        // Ligne
        val linePath =
            Path().apply {
                allPoints.firstOrNull()?.let { moveTo(it.x, it.y) }
                allPoints.drop(1).forEach { lineTo(it.x, it.y) }
            }
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
    }

    // Labels début/fin
    if (points.size >= 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(points.first().label, points.last().label).forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                    DashboardChartPoint(label = "Lun", orders = 12, customers = 3, turnover = 400.0),
                                    DashboardChartPoint(label = "Mar", orders = 20, customers = 5, turnover = 700.0),
                                    DashboardChartPoint(label = "Mer", orders = 16, customers = 4, turnover = 550.0),
                                    DashboardChartPoint(label = "Jeu", orders = 26, customers = 7, turnover = 900.0),
                                    DashboardChartPoint(label = "Ven", orders = 19, customers = 5, turnover = 650.0),
                                    DashboardChartPoint(label = "Sam", orders = 31, customers = 8, turnover = 1100.0),
                                    DashboardChartPoint(label = "Dim", orders = 38, customers = 9, turnover = 1240.5),
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
