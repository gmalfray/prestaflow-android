package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.ReviewsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.SavSummary
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat

@Composable
internal fun ReviewsSummaryCard(
    state: WidgetLoad<ReviewsSummary>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numbers = rememberNumberFormatter()
    val decimals = rememberPercentFormatter()
    NetworkWidgetCard(DashboardWidget.REVIEWS_SUMMARY, state, onRetry, modifier) { data ->
        if (data.isEmpty) {
            WidgetEmptyText()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                val rating = data.averageRating
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                    StatTile(
                        label = stringResource(R.string.dashboard_reviews_rating),
                        value =
                            if (rating != null) {
                                stringResource(R.string.dashboard_reviews_rating_value, decimals.format(rating))
                            } else {
                                stringResource(R.string.dashboard_reviews_no_rating)
                            },
                        subtitle =
                            if (rating != null) stringResource(R.string.dashboard_reviews_rating_sub, data.totalReviews) else null,
                        modifier = Modifier.weight(1f),
                        muted = rating == null,
                    )
                    StatTile(
                        label = stringResource(R.string.dashboard_reviews_count),
                        value = numbers.format(data.totalReviews),
                        modifier = Modifier.weight(1f),
                    )
                }
                val rate = data.reviewRatePercent
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
                    if (rate != null) {
                        StatTile(
                            label = stringResource(R.string.dashboard_reviews_rate),
                            value = stringResource(R.string.dashboard_percent_value, decimals.format(rate)),
                            subtitle = stringResource(R.string.dashboard_reviews_rate_sub, data.nativeReviews),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    StatTile(
                        label = stringResource(R.string.dashboard_reviews_pending),
                        value = numbers.format(data.pendingReviews),
                        modifier = Modifier.weight(1f),
                    )
                }
                data.sav?.let { SavSection(it, numbers) }
            }
        }
    }
}

@Composable
private fun SavSection(
    sav: SavSummary,
    numbers: NumberFormat,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dimensions.spacingS),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        Text(
            text = stringResource(R.string.dashboard_sav_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
            StatTile(
                label = stringResource(R.string.dashboard_sav_open),
                value = numbers.format(sav.openThreads),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.dashboard_sav_answered),
                value = numbers.format(sav.answeredThreads),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val PreviewReviews =
    ReviewsSummary(
        totalReviews = 2856,
        nativeReviews = 16,
        averageRating = 4.62,
        pendingReviews = 0,
        orders = 6721,
        reviewRatePercent = 0.2,
        sav = SavSummary(openThreads = 5, answeredThreads = 190),
    )

@Preview(name = "Avis 1.0", showBackground = true, heightDp = 1100)
@Preview(name = "Avis 1.5", showBackground = true, fontScale = 1.5f, heightDp = 1500)
@Preview(name = "Avis 2.0", showBackground = true, fontScale = 2f, heightDp = 2000)
@Composable
private fun PreviewReviewsSummary() {
    PrestaFlowTheme {
        Column(Modifier.padding(Dimensions.spacingM), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
            ReviewsSummaryCard(WidgetLoad.Data(PreviewReviews), onRetry = {})
            ReviewsSummaryCard(
                WidgetLoad.Data(PreviewReviews.copy(averageRating = null, reviewRatePercent = null, sav = null)),
                onRetry = {},
            )
        }
    }
}
