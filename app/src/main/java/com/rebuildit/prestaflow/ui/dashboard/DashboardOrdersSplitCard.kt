package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.ui.theme.Dimensions

private const val DONUT_FULL_SWEEP = 360f
private val DonutSize = 120.dp
private val DonutStroke = 18.dp

/** Part des commandes payantes, bornée à [0, 1] ; 0 sans commande. */
internal fun paidOrdersShare(
    ordersCount: Int,
    ordersFreeCount: Int,
): Float {
    if (ordersCount <= 0) return 0f
    val paid = (ordersCount - ordersFreeCount).coerceIn(0, ordersCount)
    return paid.toFloat() / ordersCount
}

/** Donut commandes payantes / offertes de la période (dérivé de `orders_count` et `orders_free_count`). */
@Composable
internal fun DashboardOrdersSplitCard(
    ordersCount: Int,
    ordersFreeCount: Int,
    modifier: Modifier = Modifier,
) {
    val paidCount = (ordersCount - ordersFreeCount).coerceAtLeast(0)
    val share = paidOrdersShare(ordersCount, ordersFreeCount)
    val paidColor = MaterialTheme.colorScheme.primary
    val freeColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val title = stringResource(id = R.string.dashboard_chart_mode_paid_vs_free)
    val description =
        "$title : ${stringResource(R.string.dashboard_legend_orders_paid)} $paidCount, " +
            "${stringResource(R.string.dashboard_legend_orders_free)} $ordersFreeCount"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingL),
            ) {
                Box(modifier = Modifier.size(DonutSize), contentAlignment = Alignment.Center) {
                    DonutRing(
                        share = share,
                        hasData = ordersCount > 0,
                        paidColor = paidColor,
                        freeColor = freeColor,
                        trackColor = trackColor,
                    )
                    Text(
                        text = ordersCount.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
                ) {
                    LegendRow(color = paidColor, label = stringResource(R.string.dashboard_legend_orders_paid), count = paidCount)
                    LegendRow(color = freeColor, label = stringResource(R.string.dashboard_legend_orders_free), count = ordersFreeCount)
                }
            }
        }
    }
}

@Composable
private fun DonutRing(
    share: Float,
    hasData: Boolean,
    paidColor: Color,
    freeColor: Color,
    trackColor: Color,
) {
    Canvas(modifier = Modifier.size(DonutSize)) {
        val stroke = DonutStroke.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        if (!hasData) {
            drawArc(trackColor, 0f, DONUT_FULL_SWEEP, false, topLeft, arcSize, style = Stroke(stroke))
            return@Canvas
        }
        val paidSweep = DONUT_FULL_SWEEP * share
        drawArc(freeColor, -90f + paidSweep, DONUT_FULL_SWEEP - paidSweep, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(paidColor, -90f, paidSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
