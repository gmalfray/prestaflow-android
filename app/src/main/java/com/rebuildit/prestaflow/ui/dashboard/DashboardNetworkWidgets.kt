package com.rebuildit.prestaflow.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val DATE_PREFIX_LENGTH = 10
private const val PODIUM_SIZE = 3
private val RankBubbleSize = 28.dp
private val OrderTrailingMaxWidth = 150.dp
private val ProductTrailingMaxWidth = 120.dp

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
            WidgetCardHeader(
                widget = widget,
                modifier =
                    if (onHeaderClick != null) {
                        Modifier.heightIn(min = Dimensions.minTouchTarget).clickable(role = Role.Button, onClick = onHeaderClick)
                    } else {
                        Modifier
                    },
            )
            when (state) {
                is WidgetLoad.Data -> content(state.value)
                WidgetLoad.Error ->
                    WidgetErrorRow(message = stringResource(id = R.string.dashboard_widget_error), onRetry = onRetry)
                else -> WidgetSkeleton()
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
            WidgetEmptyText()
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
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = null, role = Role.Button, onClick = onClick)
                .heightIn(min = Dimensions.minTouchTarget)
                .padding(vertical = Dimensions.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = order.customerName.ifBlank { order.reference },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
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
        Column(
            modifier = Modifier.widthIn(max = OrderTrailingMaxWidth),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            Text(
                text = currency.format(order.totalPaid),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            WidgetEmptyText()
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
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouchTarget).padding(vertical = Dimensions.spacingS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        RankBubble(rank = rank)
        Text(
            text = product.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Column(
            modifier = Modifier.widthIn(max = ProductTrailingMaxWidth),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = stringResource(id = R.string.dashboard_widget_top_qty, product.quantity),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = currency.format(product.totalTaxIncl),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Pastille de rang : le podium (1 à 3) est teinté, les suivants restent neutres. */
@Composable
private fun RankBubble(rank: Int) {
    val podium = rank <= PODIUM_SIZE
    val container = if (podium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (podium) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.size(RankBubbleSize).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = rank.toString(), style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** En-tête de carte : icône du widget dans une pastille teintée, puis titre en petites capitales. */
@Composable
internal fun WidgetCardHeader(
    widget: DashboardWidget,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
    ) {
        Icon(
            imageVector = widget.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimensions.iconSizeSmall),
        )
        Text(
            text = stringResource(id = widget.titleRes()).uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PreviewOrders =
    listOf(
        LatestOrder(
            1,
            "XKBRTQWPD",
            "Marie-Charlotte de la Fontaine-Beauregard",
            129.9,
            "EUR",
            "En cours de préparation",
            "#FFB74D",
            "2026-11-08",
        ),
        LatestOrder(2, "ABCDEFGHI", "Paul Martin", 18.0, "EUR", "Paiement accepté", null, "2026-11-07"),
    )

private val PreviewProducts =
    listOf(
        TopProduct(1, "Pelote mérinos extra fine coloris bleu nuit 50 g", 42, 378.0),
        TopProduct(2, "Aiguilles circulaires bambou", 17, 203.5),
        TopProduct(3, "Kit crochet \"Lapin\"", 9, 161.1),
        TopProduct(4, "Bonnet", 4, 40.0),
    )

@Composable
private fun PreviewWidgetsColumn() {
    PrestaFlowTheme {
        Column(modifier = Modifier.padding(Dimensions.spacingM), verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM)) {
            LatestOrdersCard(WidgetLoad.Data(PreviewOrders), onRetry = {}, onOrderClick = {}, onHeaderClick = {})
            TopProductsCard(WidgetLoad.Data(PreviewProducts), onRetry = {})
            LatestOrdersCard(WidgetLoad.Loading, onRetry = {}, onOrderClick = {}, onHeaderClick = {})
            TopProductsCard(WidgetLoad.Error, onRetry = {})
            TopProductsCard(WidgetLoad.Data(emptyList()), onRetry = {})
        }
    }
}

@Preview(name = "Cartes liste 1.0", showBackground = true, heightDp = 1100)
@Preview(name = "Cartes liste 1.5", showBackground = true, fontScale = 1.5f, heightDp = 1500)
@Preview(name = "Cartes liste 2.0", showBackground = true, fontScale = 2f, heightDp = 2000)
@Composable
private fun PreviewListCards() = PreviewWidgetsColumn()
