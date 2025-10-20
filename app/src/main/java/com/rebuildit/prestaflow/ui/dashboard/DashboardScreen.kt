package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DashboardRoute(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        modifier = modifier.fillMaxSize(),
        state = state,
        onPeriodSelected = viewModel::onPeriodSelected,
        onRefresh = viewModel::onRefresh
    )
}

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    state: DashboardUiState,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onRefresh: () -> Unit
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.snapshot == null -> LoadingState(modifier)
        state.snapshot == null && errorMessage != null -> ErrorState(
            modifier = modifier,
            message = errorMessage,
            onRetry = onRefresh
        )
        state.snapshot == null -> EmptyState(modifier)
        else -> DashboardContent(
            modifier = modifier,
            snapshot = state.snapshot,
            selectedPeriod = state.selectedPeriod,
            errorMessage = errorMessage,
            isRefreshing = state.isRefreshing,
            onPeriodSelected = onPeriodSelected,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ErrorState(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(id = R.string.action_retry))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_state_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardContent(
    modifier: Modifier,
    snapshot: DashboardSnapshot,
    selectedPeriod: DashboardPeriod,
    errorMessage: String?,
    isRefreshing: Boolean,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onRefresh: () -> Unit
) {
    val currencyFormatter = rememberCurrencyFormatter()
    val numberFormatter = rememberNumberFormatter()
    val turnoverText = remember(snapshot.turnover) { currencyFormatter.format(snapshot.turnover) }
    val ordersText = remember(snapshot.ordersCount) { numberFormatter.format(snapshot.ordersCount) }
    val customersText =
        remember(snapshot.customersCount) { numberFormatter.format(snapshot.customersCount) }
    val productsText =
        remember(snapshot.productsCount) { numberFormatter.format(snapshot.productsCount) }
    val lastUpdatedText = remember(snapshot.lastUpdatedIso) { formatLastUpdated(snapshot.lastUpdatedIso) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            DashboardPeriodRow(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                onRefresh = onRefresh,
                isRefreshing = isRefreshing
            )
        }

        if (errorMessage != null) {
            item {
                AssistChip(
                    onClick = onRefresh,
                    label = { Text(text = errorMessage) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }

        item {
            DashboardKpiGrid(
                items = listOf(
                    DashboardKpi(
                        title = stringResource(id = R.string.dashboard_kpi_turnover),
                        value = turnoverText
                    ),
                    DashboardKpi(
                        title = stringResource(id = R.string.dashboard_kpi_orders),
                        value = ordersText
                    ),
                    DashboardKpi(
                        title = stringResource(id = R.string.dashboard_kpi_customers),
                        value = customersText
                    ),
                    DashboardKpi(
                        title = stringResource(id = R.string.dashboard_kpi_products),
                        value = productsText
                    )
                )
            )
        }

        item {
            DashboardChartCard(points = snapshot.chart)
        }

        item {
            Text(
                text = stringResource(id = R.string.label_last_sync, lastUpdatedText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardKpiGrid(items: List<DashboardKpi>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { item ->
                    KpiCard(
                        modifier = Modifier.weight(1f),
                        title = item.title,
                        value = item.value
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
private fun DashboardChartCard(
    points: List<DashboardChartPoint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_chart_title_turnover),
                style = MaterialTheme.typography.titleMedium
            )
            TurnoverChart(points = points, height = 180.dp)
        }
    }
}

@Composable
private fun TurnoverChart(
    points: List<DashboardChartPoint>,
    height: Dp,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Text(
            text = stringResource(id = R.string.dashboard_chart_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val maxValue = points.maxOf { it.turnover }.takeIf { it > 0.0 } ?: 1.0
        val verticalPadding = size.height * 0.1f
        val usableHeight = size.height - (verticalPadding * 2)
        val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)

        drawLine(
            color = axisColor,
            start = Offset(x = 0f, y = size.height - verticalPadding),
            end = Offset(x = size.width, y = size.height - verticalPadding),
            strokeWidth = 1.dp.toPx()
        )

        val linePoints = points.mapIndexed { index, point ->
            val normalized = (point.turnover / maxValue).coerceIn(0.0, 1.0)
            val x = stepX * index
            val y = size.height - verticalPadding - (usableHeight * normalized.toFloat())
            Offset(x, y)
        }

        val linePath = Path().apply {
            linePoints.firstOrNull()?.let { moveTo(it.x, it.y) }
            linePoints.drop(1).forEach { lineTo(it.x, it.y) }
        }

        val fillPath = Path().apply {
            linePoints.firstOrNull()?.let { firstPoint ->
                moveTo(firstPoint.x, size.height - verticalPadding)
                linePoints.forEach { lineTo(it.x, it.y) }
                linePoints.lastOrNull()?.let { lastPoint ->
                    lineTo(lastPoint.x, size.height - verticalPadding)
                }
                close()
            }
        }

        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        linePoints.forEach { point ->
            drawCircle(
                color = lineColor,
                radius = 4.dp.toPx(),
                center = point
            )
        }
    }

    if (points.size >= 2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labels = listOf(points.first().label, points.last().label)
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Text(
            text = points.first().label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardPeriodRow(
    selectedPeriod: DashboardPeriod,
    onPeriodSelected: (DashboardPeriod) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SingleChoiceSegmentedButtonRow {
            val periods = DashboardPeriod.values()
            periods.forEachIndexed { index, period ->
                val text = when (period) {
                    DashboardPeriod.TODAY -> stringResource(id = R.string.dashboard_period_today)
                    DashboardPeriod.WEEK -> stringResource(id = R.string.dashboard_period_week)
                    DashboardPeriod.MONTH -> stringResource(id = R.string.dashboard_period_month)
                    DashboardPeriod.QUARTER -> stringResource(id = R.string.dashboard_period_quarter)
                    DashboardPeriod.YEAR -> stringResource(id = R.string.dashboard_period_year)
                }
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = periods.size
                    ),
                    onClick = { onPeriodSelected(period) },
                    selected = period == selectedPeriod
                ) {
                    Text(text = text)
                }
            }
        }

        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(id = R.string.dashboard_action_refresh)
                )
            }
        }
    }
}

@Composable
private fun UiText.asString(): String = when (this) {
    is UiText.Dynamic -> value
    is UiText.FromResources -> stringResource(id = resId, *args.toTypedArray())
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

private fun formatLastUpdated(isoString: String): String {
    return runCatching {
        val instant = Instant.parse(isoString)
        val zoned = instant.atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(zoned)
    }.getOrElse { isoString }
}

private data class DashboardKpi(
    val title: String,
    val value: String
)
