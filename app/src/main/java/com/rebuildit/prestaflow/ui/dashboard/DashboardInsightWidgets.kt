package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.AcquisitionChannel
import com.rebuildit.prestaflow.domain.dashboard.model.AcquisitionChannels
import com.rebuildit.prestaflow.domain.dashboard.model.CustomerRetention
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohort
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversionCohorts
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val PERCENT = 100
private const val IMMATURE_ALPHA = 0.6f
private val TileShape = RoundedCornerShape(12.dp)

// ─── Helpers purs (testés) ────────────────────────────────────────────────────

/** Nom d'un canal sans traduction : réseaux sociaux (noms propres) ou valeur utm libre, capitalisée. */
internal fun channelFallbackLabel(key: String): String =
    when (key.trim().lowercase()) {
        "instagram" -> "Instagram"
        "facebook" -> "Facebook"
        "pinterest" -> "Pinterest"
        else -> key.trim().replaceFirstChar { it.uppercase() }
    }

/** Ressource du libellé traduit d'un canal ; `null` pour les noms propres et les clés inconnues. */
internal fun channelLabelRes(key: String): Int? =
    when (key.trim().lowercase()) {
        "direct" -> R.string.dashboard_channel_direct
        "recherche" -> R.string.dashboard_channel_search
        "email" -> R.string.dashboard_channel_email
        "autre" -> R.string.dashboard_channel_other
        else -> null
    }

/** « Mai 2026 » depuis `2026-05`, dans la langue [locale] ; texte brut si illisible. */
internal fun formatCohortMonth(
    month: String,
    locale: Locale,
): String =
    runCatching {
        YearMonth.parse(month).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { it.uppercase(locale) }
    }.getOrDefault(month)

/** Cohorte du mois en cours (ou futur) : moins de 90 jours de recul, son taux à 90 j est partiel. */
internal fun isImmatureCohort(
    month: String,
    current: YearMonth,
): Boolean = runCatching { YearMonth.parse(month) >= current }.getOrDefault(false)

/** Plus grand taux à 90 j de la liste : sert d'échelle aux barres (le taux brut, souvent < 10 %, rendrait les barres illisibles). */
internal fun maxRate90(cohorts: List<FreeConversionCohort>): Double = cohorts.maxOfOrNull { it.rate90Percent } ?: 0.0

@Composable
internal fun rememberLocale(): Locale {
    val locales = LocalConfiguration.current.locales
    return if (!locales.isEmpty) locales[0] else Locale.getDefault()
}

@Composable
internal fun rememberPercentFormatter(): NumberFormat {
    val locale = rememberLocale()
    return remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 } }
}

// ─── A : Fidélité / réachat ───────────────────────────────────────────────────

@Composable
internal fun CustomerRetentionCard(
    state: WidgetLoad<CustomerRetention>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = rememberPercentFormatter()
    val numbers = rememberNumberFormatter()
    NetworkWidgetCard(DashboardWidget.CUSTOMER_RETENTION, state, onRetry, modifier) { data ->
        if (data.isEmpty) {
            WidgetEmptyText()
        } else {
            val median = data.medianDaysBetweenOrders
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                    StatTile(
                        label = stringResource(R.string.dashboard_retention_rate),
                        value = stringResource(R.string.dashboard_percent_value, percent.format(data.repeatRatePercent)),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = stringResource(R.string.dashboard_retention_median),
                        value =
                            if (median != null) {
                                stringResource(R.string.dashboard_retention_days, median.roundToInt())
                            } else {
                                stringResource(R.string.dashboard_widget_unavailable_short)
                            },
                        modifier = Modifier.weight(1f),
                        muted = median == null,
                    )
                }
                StatTile(
                    label = stringResource(R.string.dashboard_retention_paying),
                    value = numbers.format(data.payingCustomers),
                    subtitle =
                        stringResource(R.string.dashboard_retention_split, data.recurringCustomers, data.newCustomers),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Tuile de statistique : libellé discret, valeur en évidence, sous-texte optionnel. */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    muted: Boolean = false,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLow, TileShape)
                .padding(Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = if (muted) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── B : Acquisition par canal ────────────────────────────────────────────────

@Composable
internal fun AcquisitionChannelsCard(
    state: WidgetLoad<AcquisitionChannels>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    NetworkWidgetCard(DashboardWidget.ACQUISITION_CHANNELS, state, onRetry, modifier) { data ->
        if (data.isEmpty) {
            WidgetEmptyText()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
                Text(
                    text = stringResource(R.string.dashboard_channels_subtitle, data.windowDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                data.channels.forEach { channel ->
                    ChannelRow(channel = channel, share = data.shareOf(channel), numbers = numbers)
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: AcquisitionChannel,
    share: Float,
    numbers: NumberFormat,
) {
    val labelRes = channelLabelRes(channel.key)
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            Text(
                text = if (labelRes != null) stringResource(labelRes) else channelFallbackLabel(channel.key),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.dashboard_channels_value, numbers.format(channel.orders), (share * PERCENT).roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        ShareBar(share = share, color = MaterialTheme.colorScheme.primary)
    }
}

// ─── C : Cohortes offert → payant ─────────────────────────────────────────────

@Composable
internal fun FreeCohortsCard(
    state: WidgetLoad<FreeConversionCohorts>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    currentMonth: YearMonth = remember { YearMonth.now() },
) {
    val locale = rememberLocale()
    val percent = rememberPercentFormatter()
    NetworkWidgetCard(DashboardWidget.FREE_CONVERSION_COHORTS, state, onRetry, modifier) { data ->
        val cohorts = data.cohorts.filter { it.firstFreeCustomers > 0 }
        if (cohorts.isEmpty()) {
            WidgetEmptyText()
        } else {
            val scale = maxRate90(cohorts)
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                Text(
                    text = stringResource(R.string.dashboard_cohorts_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cohorts.forEachIndexed { index, cohort ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CohortRow(
                        cohort = cohort,
                        month = formatCohortMonth(cohort.month, locale),
                        immature = isImmatureCohort(cohort.month, currentMonth),
                        barShare = if (scale > 0.0) (cohort.rate90Percent / scale).toFloat() else 0f,
                        percent = percent,
                    )
                }
            }
        }
    }
}

@Composable
private fun CohortRow(
    cohort: FreeConversionCohort,
    month: String,
    immature: Boolean,
    barShare: Float,
    percent: NumberFormat,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingXs).alpha(if (immature) IMMATURE_ALPHA else 1f),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        ) {
            Text(
                text = if (immature) stringResource(R.string.dashboard_cohorts_current_month, month) else month,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.dashboard_cohorts_free_count, cohort.firstFreeCustomers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = stringResource(R.string.dashboard_cohorts_rate_90, percent.format(cohort.rate90Percent)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ShareBar(share = barShare, color = MaterialTheme.colorScheme.primary)
        Text(
            text =
                stringResource(
                    R.string.dashboard_cohorts_rate_30_60,
                    percent.format(cohort.rate30Percent),
                    percent.format(cohort.rate60Percent),
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val PreviewRetention = CustomerRetention(820, 310, 37.8, 64.5, 510, 310)
private val PreviewChannels =
    AcquisitionChannels(
        listOf(AcquisitionChannel("recherche", 118), AcquisitionChannel("instagram", 63), AcquisitionChannel("direct", 44)),
        30,
    )
private val PreviewCohorts =
    FreeConversionCohorts(
        listOf(
            FreeConversionCohort("2026-11", 12, 0, 0, 0),
            FreeConversionCohort("2026-05", 40, 1, 2, 3),
            FreeConversionCohort("2026-04", 55, 3, 5, 8),
        ),
    )

@Preview(name = "Insights 1.0", showBackground = true, heightDp = 1400)
@Preview(name = "Insights 1.5", showBackground = true, fontScale = 1.5f, heightDp = 1800)
@Preview(name = "Insights 2.0", showBackground = true, fontScale = 2f, heightDp = 2400)
@Composable
private fun PreviewInsightWidgets() {
    PrestaFlowTheme {
        Column(Modifier.padding(Dimensions.spacingM), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
            CustomerRetentionCard(WidgetLoad.Data(PreviewRetention), onRetry = {})
            CustomerRetentionCard(WidgetLoad.Data(PreviewRetention.copy(medianDaysBetweenOrders = null)), onRetry = {})
            AcquisitionChannelsCard(WidgetLoad.Data(PreviewChannels), onRetry = {})
            FreeCohortsCard(WidgetLoad.Data(PreviewCohorts), onRetry = {}, currentMonth = YearMonth.of(2026, 11))
            FreeCohortsCard(WidgetLoad.Error, onRetry = {})
        }
    }
}
