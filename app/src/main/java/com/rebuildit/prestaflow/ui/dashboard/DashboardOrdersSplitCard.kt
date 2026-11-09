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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import kotlin.math.roundToInt

private const val DONUT_FULL_SWEEP = 360f
private const val DONUT_START_ANGLE = -90f
private const val DONUT_GAP_DEGREES = 3f
private const val PERCENT = 100f
private val DonutSize = 124.dp
private val DonutStroke = 16.dp
private val LegendMarkerSize = 14.dp
private val LegendRingWidth = 2.dp

/** Part des commandes payantes, bornée à [0, 1] ; 0 sans commande. */
internal fun paidOrdersShare(
    ordersCount: Int,
    ordersFreeCount: Int,
): Float {
    if (ordersCount <= 0) return 0f
    val paid = (ordersCount - ordersFreeCount).coerceIn(0, ordersCount)
    return paid.toFloat() / ordersCount
}

/**
 * Donut commandes payantes / offertes de la période (dérivé de `orders_count` et `orders_free_count`).
 * Les deux parts ne sont pas distinguées par la couleur seule : la légende donne le compte et le
 * pourcentage, et les marqueurs diffèrent par la forme (disque plein / anneau).
 */
@Composable
internal fun DashboardOrdersSplitCard(
    ordersCount: Int,
    ordersFreeCount: Int,
    modifier: Modifier = Modifier,
) {
    val paidCount = (ordersCount - ordersFreeCount).coerceAtLeast(0)
    val freeCount = ordersCount - paidCount
    val share = paidOrdersShare(ordersCount, ordersFreeCount)
    val paidPercent = (share * PERCENT).roundToInt()
    val freePercent = if (ordersCount > 0) PERCENT.roundToInt() - paidPercent else 0
    val paidColor = MaterialTheme.colorScheme.primary
    val freeColor = MaterialTheme.colorScheme.primaryContainer
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val paidLabel = stringResource(R.string.dashboard_legend_orders_paid)
    val freeLabel = stringResource(R.string.dashboard_legend_orders_free)
    val description = splitDescription(ordersCount, paidCount, freeCount, paidPercent, freePercent)
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
            WidgetCardHeader(widget = DashboardWidget.ORDERS_SPLIT)
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingL),
            ) {
                DonutWithTotal(
                    share = share,
                    ordersCount = ordersCount,
                    paidColor = paidColor,
                    freeColor = freeColor,
                    trackColor = trackColor,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
                ) {
                    LegendRow(
                        marker = { MarkerDisc(paidColor) },
                        label = paidLabel,
                        count = paidCount,
                        percent = paidPercent.takeIf { ordersCount > 0 },
                    )
                    LegendRow(
                        marker = { MarkerRing(freeColor) },
                        label = freeLabel,
                        count = freeCount,
                        percent = freePercent.takeIf { ordersCount > 0 },
                    )
                }
            }
        }
    }
}

@Composable
private fun splitDescription(
    ordersCount: Int,
    paidCount: Int,
    freeCount: Int,
    paidPercent: Int,
    freePercent: Int,
): String =
    stringResource(
        id = R.string.dashboard_split_description,
        stringResource(R.string.dashboard_chart_mode_paid_vs_free),
        ordersCount,
        stringResource(R.string.dashboard_legend_orders_paid),
        paidCount,
        paidPercent,
        stringResource(R.string.dashboard_legend_orders_free),
        freeCount,
        freePercent,
    )

@Composable
private fun DonutWithTotal(
    share: Float,
    ordersCount: Int,
    paidColor: Color,
    freeColor: Color,
    trackColor: Color,
) {
    Box(modifier = Modifier.size(DonutSize), contentAlignment = Alignment.Center) {
        DonutRing(share = share, hasData = ordersCount > 0, paidColor = paidColor, freeColor = freeColor, trackColor = trackColor)
        Column(
            modifier = Modifier.padding(horizontal = DonutStroke + Dimensions.spacingXs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = ordersCount.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(id = R.string.dashboard_split_total_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        if (!hasData || share <= 0f || share >= 1f) {
            // Vide ou 100 % d'une seule part : un anneau plein, sans coupure à raccorder.
            val color =
                if (!hasData) {
                    trackColor
                } else if (share >= 1f) {
                    paidColor
                } else {
                    freeColor
                }
            drawArc(color, 0f, DONUT_FULL_SWEEP, false, topLeft, arcSize, style = Stroke(stroke))
            return@Canvas
        }
        val paidSweep = DONUT_FULL_SWEEP * share
        val half = DONUT_GAP_DEGREES / 2
        val paidDraw = (paidSweep - DONUT_GAP_DEGREES).coerceAtLeast(0f)
        val freeDraw = (DONUT_FULL_SWEEP - paidSweep - DONUT_GAP_DEGREES).coerceAtLeast(0f)
        drawArc(freeColor, DONUT_START_ANGLE + paidSweep + half, freeDraw, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(paidColor, DONUT_START_ANGLE + half, paidDraw, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
    }
}

@Composable
private fun MarkerDisc(color: Color) {
    Canvas(modifier = Modifier.size(LegendMarkerSize)) { drawCircle(color) }
}

/** Anneau rempli d'une teinte claire : se distingue du disque plein sans dépendre de la couleur. */
@Composable
private fun MarkerRing(color: Color) {
    val outline = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(LegendMarkerSize)) {
        drawCircle(color)
        drawCircle(outline, radius = size.minDimension / 2 - LegendRingWidth.toPx() / 2, style = Stroke(LegendRingWidth.toPx()))
    }
}

@Composable
private fun LegendRow(
    marker: @Composable () -> Unit,
    label: String,
    count: Int,
    percent: Int?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
        marker()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (percent != null) {
                Text(
                    text = stringResource(id = R.string.dashboard_split_percent, percent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Preview(name = "Donut 1.0", showBackground = true)
@Preview(name = "Donut 1.5", showBackground = true, fontScale = 1.5f)
@Preview(name = "Donut 2.0", showBackground = true, fontScale = 2f)
@Composable
private fun PreviewOrdersSplit() {
    PrestaFlowTheme {
        Column(modifier = Modifier.padding(Dimensions.spacingM), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
            DashboardOrdersSplitCard(ordersCount = 48, ordersFreeCount = 17)
            DashboardOrdersSplitCard(ordersCount = 0, ordersFreeCount = 0)
        }
    }
}
