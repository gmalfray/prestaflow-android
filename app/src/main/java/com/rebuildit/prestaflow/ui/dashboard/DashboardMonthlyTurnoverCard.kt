package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.MonthlyTurnover
import com.rebuildit.prestaflow.domain.dashboard.model.MonthlyTurnoverPoint
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ChartHeight = 112.dp
private val BarGap = 4.dp
private val BarTopShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
private const val BAR_ALPHA = 0.5f
private const val PARTIAL_FACTOR = 0.5f
private const val MIN_BAR_FRACTION = 0.03f
private const val LARGE_FONT_SCALE = 1.3f
private const val LABEL_STEP_NORMAL = 2
private const val LABEL_STEP_LARGE = 3
private const val BASELINE_HEIGHT_DP = 2
private const val PREVIEW_AVG_CART = 40

// ─── Helpers purs (testés) ────────────────────────────────────────────────────

/** Mois abrégé localisé depuis `yyyy-MM` (« janv », « déc »), sans le point final ; texte brut si illisible. */
internal fun formatMonthShort(
    month: String,
    locale: Locale,
): String =
    runCatching { YearMonth.parse(month).format(DateTimeFormatter.ofPattern("LLL", locale)).trimEnd('.') }
        .getOrDefault(month)

/** Hauteur relative (0..1) de chaque barre, rapportée au pic ; les valeurs négatives ou nulles donnent 0. */
internal fun monthlyBarFractions(months: List<MonthlyTurnoverPoint>): List<Float> {
    val peak = months.maxOfOrNull { it.turnover } ?: 0.0
    return months.map { if (peak > 0.0 && it.turnover > 0.0) (it.turnover / peak).toFloat().coerceIn(0f, 1f) else 0f }
}

/** Une étiquette sur [monthLabelStep], en comptant depuis le dernier mois pour toujours étiqueter le mois en cours. */
internal fun isMonthLabelShown(
    index: Int,
    lastIndex: Int,
    step: Int,
): Boolean = (lastIndex - index) % step == 0

/** Police agrandie : on espace davantage les étiquettes pour qu'elles restent lisibles. */
internal fun monthLabelStep(fontScale: Float): Int = if (fontScale > LARGE_FONT_SCALE) LABEL_STEP_LARGE else LABEL_STEP_NORMAL

// ─── Carte ────────────────────────────────────────────────────────────────────

@Composable
internal fun MonthlyTurnoverCard(
    state: WidgetLoad<MonthlyTurnover>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentMonth: YearMonth = remember { YearMonth.now() },
) {
    val locale = rememberLocale()
    val currency = rememberCurrencyFormatter()
    NetworkWidgetCard(DashboardWidget.MONTHLY_TURNOVER, state, onRetry, modifier) { data ->
        if (data.isEmpty) {
            WidgetEmptyText()
        } else {
            val peak = data.peakMonths
            val peakLabel = peak.joinToString(", ") { formatCohortMonth(it.month, locale) }
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
                Text(
                    text = stringResource(R.string.dashboard_monthly_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MonthlyBars(data = data, locale = locale, currentMonth = currentMonth, peakLabel = peakLabel)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                    StatTile(
                        label = stringResource(R.string.dashboard_monthly_total),
                        value = currency.format(data.total),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.dashboard_monthly_peak),
                        value = currency.format(data.peakTurnover),
                        subtitle = peakLabel,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthlyBars(
    data: MonthlyTurnover,
    locale: Locale,
    currentMonth: YearMonth,
    peakLabel: String,
) {
    val months = data.months
    val hasPartial = months.lastOrNull()?.let { isImmatureCohort(it.month, currentMonth) } == true
    val chartDescription = stringResource(R.string.dashboard_monthly_chart_description, peakLabel)
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = chartDescription },
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
    ) {
        BarsRow(data = data, partialLast = hasPartial)
        MonthLabelsRow(months = months, locale = locale)
        if (hasPartial) {
            Text(
                text = stringResource(R.string.dashboard_monthly_partial, formatMonthShort(months.last().month, locale)),
                modifier = Modifier.padding(top = Dimensions.spacingXs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BarsRow(
    data: MonthlyTurnover,
    partialLast: Boolean,
) {
    val months = data.months
    val fractions = remember(months) { monthlyBarFractions(months) }
    val peakMonthKeys = remember(data) { data.peakMonths.map { it.month }.toSet() }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().height(ChartHeight),
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEachIndexed { index, point ->
            val partial = index == months.lastIndex && partialLast
            val base = if (point.month in peakMonthKeys) primary else primary.copy(alpha = BAR_ALPHA)
            val color = if (partial) base.copy(alpha = base.alpha * PARTIAL_FACTOR) else base
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                val fraction = fractions[index]
                if (fraction > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(MIN_BAR_FRACTION))
                                .background(color, BarTopShape),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(BASELINE_HEIGHT_DP.dp).background(track, BarTopShape))
                }
            }
        }
    }
}

@Composable
private fun MonthLabelsRow(
    months: List<MonthlyTurnoverPoint>,
    locale: Locale,
) {
    val step = monthLabelStep(LocalDensity.current.fontScale)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
        months.forEachIndexed { index, point ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (isMonthLabelShown(index, months.lastIndex, step)) {
                    Text(
                        text = formatMonthShort(point.month, locale),
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val PreviewMonthly =
    MonthlyTurnover(
        listOf(2411.5, 1980.0, 1620.4, 1405.0, 1288.9, 1510.0, 1722.3, 2105.8, 2890.0, 3920.7, 4310.2, 1160.0)
            .mapIndexed { i, ca ->
                MonthlyTurnoverPoint(YearMonth.of(2025, 12).plusMonths(i.toLong()).toString(), ca, ca.toInt() / PREVIEW_AVG_CART)
            },
    )

@Preview(name = "Saisonnalité 1.0", showBackground = true, heightDp = 700)
@Preview(name = "Saisonnalité 1.5", showBackground = true, fontScale = 1.5f, heightDp = 800)
@Preview(name = "Saisonnalité 2.0", showBackground = true, fontScale = 2f, heightDp = 900)
@Composable
private fun PreviewMonthlyTurnover() {
    PrestaFlowTheme {
        Column(Modifier.padding(Dimensions.spacingM), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
            MonthlyTurnoverCard(WidgetLoad.Data(PreviewMonthly), onRetry = {}, currentMonth = YearMonth.of(2026, 11))
            MonthlyTurnoverCard(WidgetLoad.Error, onRetry = {})
        }
    }
}
