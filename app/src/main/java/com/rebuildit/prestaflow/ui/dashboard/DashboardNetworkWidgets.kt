package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.theme.Dimensions
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val DATE_PREFIX_LENGTH = 10

/** Date courte localisée depuis un horodatage `yyyy-MM-dd…` ; texte brut si illisible. */
internal fun formatShortDate(iso: String?): String =
    iso?.let { raw ->
        runCatching {
            LocalDate.parse(raw.take(DATE_PREFIX_LENGTH)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))
        }.getOrDefault(raw)
    }.orEmpty()

/** Carte pleine largeur d'un widget réseau : titre, puis chargement / erreur locale / contenu. */
@Suppress("LongParameterList")
@Composable
private fun <T> NetworkWidgetCard(
    widget: DashboardWidget,
    state: WidgetLoad<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            Text(
                text = stringResource(id = widget.titleRes()).uppercase(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (onHeaderClick != null) Modifier.clickable(onClick = onHeaderClick) else Modifier),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                is WidgetLoad.Data -> content(state.value)
                WidgetLoad.Error ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = R.string.dashboard_widget_error),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(id = R.string.action_retry)) }
                    }
                else -> CircularProgressIndicator(modifier = Modifier.padding(vertical = Dimensions.spacingS))
            }
        }
    }
}

@Composable
internal fun LatestOrdersCard(
    state: WidgetLoad<List<LatestOrder>>,
    onRetry: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = rememberCurrencyFormatter()
    NetworkWidgetCard(DashboardWidget.LATEST_ORDERS, state, onRetry, modifier, onHeaderClick) { orders ->
        if (orders.isEmpty()) {
            EmptyWidgetText()
        } else {
            orders.forEachIndexed { index, order ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LatestOrderRow(order, currency, onClick = { onOrderClick(order.id) })
            }
        }
    }
}

@Composable
private fun LatestOrderRow(
    order: LatestOrder,
    currency: NumberFormat,
    onClick: () -> Unit,
) {
    val date = remember(order.createdAtIso) { formatShortDate(order.createdAtIso) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = order.customerName.ifBlank { order.reference },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(order.reference, date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            Text(text = currency.format(order.totalPaid), style = MaterialTheme.typography.titleSmall)
            OrderStatusBadge(status = order.status, statusColor = order.statusColor)
        }
    }
}

@Composable
internal fun TopProductsCard(
    state: WidgetLoad<List<TopProduct>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currency = rememberCurrencyFormatter()
    NetworkWidgetCard(DashboardWidget.TOP_PRODUCTS, state, onRetry, modifier) { products ->
        if (products.isEmpty()) {
            EmptyWidgetText()
        } else {
            products.forEachIndexed { index, product ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TopProductRow(rank = index + 1, product = product, currency = currency)
            }
        }
    }
}

@Composable
private fun TopProductRow(
    rank: Int,
    product: TopProduct,
    currency: NumberFormat,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Dimensions.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = product.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(id = R.string.dashboard_widget_top_qty, product.quantity),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = currency.format(product.totalTaxIncl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyWidgetText() {
    Text(
        text = stringResource(id = R.string.dashboard_widget_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
