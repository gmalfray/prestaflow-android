package com.rebuildit.prestaflow.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OrdersRoute(
    onOrderClick: (Long) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OrdersScreen(
        uiState = uiState,
        onRefresh = { forceRemote -> viewModel.refresh(forceRemote, notifyOnError = true) },
        onOrderClick = onOrderClick,
        onQueryChange = viewModel::onQueryChange,
    )
}

@Composable
fun OrdersScreen(
    uiState: OrdersUiState,
    onRefresh: (Boolean) -> Unit,
    onOrderClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
) {
    val errorMessage = uiState.error?.asString()

    when {
        uiState.isLoading && uiState.orders.isEmpty() -> LoadingState(modifier)
        uiState.orders.isEmpty() ->
            EmptyState(
                message = stringResource(R.string.orders_list_empty),
                modifier = modifier,
                errorMessage = errorMessage,
                onRefresh = { onRefresh(true) },
            )
        else ->
            OrdersList(
                modifier = modifier,
                orders = uiState.visibleOrders,
                totalCount = uiState.orders.size,
                query = uiState.query,
                onQueryChange = onQueryChange,
                isRefreshing = uiState.isRefreshing,
                errorMessage = errorMessage,
                onRefresh = { onRefresh(true) },
                onOrderClick = onOrderClick,
            )
    }
}

@Suppress("LongParameterList")
@Composable
private fun OrdersList(
    modifier: Modifier,
    orders: List<Order>,
    totalCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOrderClick: (Long) -> Unit,
) {
    val dateFormatter = rememberDateFormatter()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Indicateur de rafraîchissement
            AnimatedVisibility(visible = isRefreshing, enter = fadeIn(), exit = fadeOut()) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            }

            // Bandeau erreur
            if (errorMessage != null) {
                ErrorRow(message = errorMessage, onRefresh = onRefresh)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        horizontal = Dimensions.screenEdgeMargin,
                        vertical = Dimensions.spacingL,
                    ),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            ) {
                // En-tête : nombre de commandes
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.orders_list_section_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.orders_list_count, totalCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Champ de recherche
                item {
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.orders_search_placeholder),
                    )
                }

                if (orders.isEmpty()) {
                    // Recherche sans résultat
                    item {
                        Text(
                            text = stringResource(R.string.list_no_results, query),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Dimensions.spacingM),
                        )
                    }
                } else {
                    // Carte conteneur groupée
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column {
                                orders.forEachIndexed { index, order ->
                                    OrderRow(
                                        order = order,
                                        dateFormatter = dateFormatter,
                                        onClick = { onOrderClick(order.id) },
                                    )
                                    if (index < orders.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            thickness = 1.dp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ligne de commande — design Stitch : avatar initiales, nom + référence,
 * badge statut coloré, montant aligné à droite.
 */
@Composable
private fun OrderRow(
    order: Order,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val amountText =
        remember(order.totalPaid, order.currency) {
            formatCurrency(order.totalPaid, order.currency)
        }
    val updatedAt =
        remember(order.updatedAtIso) {
            formatTimestamp(order.updatedAtIso, dateFormatter) ?: order.updatedAtIso
        }
    val status = order.status.ifBlank { stringResource(id = R.string.orders_status_unknown) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(R.string.orders_action_open),
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { role = Role.Button }
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar initiales
        AvatarInitials(
            name = order.customerName.ifBlank { order.reference },
        )

        // Contenu central
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            // Nom + montant sur la même ligne
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = order.customerName.ifBlank { stringResource(R.string.orders_customer_unknown) },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = order.reference,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    modifier = Modifier.padding(start = Dimensions.spacingS),
                    text = amountText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Date + badge statut
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = updatedAt,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OrderStatusBadge(status = status)
            }
        }
    }
}

@Composable
private fun rememberDateFormatter(): DateTimeFormatter =
    remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    }

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Commandes — liste")
@Composable
private fun PreviewOrdersList() {
    PrestaFlowTheme {
        OrdersScreen(
            uiState =
                OrdersUiState(
                    orders =
                        listOf(
                            Order(
                                id = 1L,
                                reference = "#ORD-8492",
                                status = "En attente",
                                totalPaid = 45.0,
                                currency = "EUR",
                                customerName = "Marie Dupont",
                                updatedAtIso = "2026-06-19T14:20:00Z",
                            ),
                            Order(
                                id = 2L,
                                reference = "#ORD-8491",
                                status = "Expédiée",
                                totalPaid = 28.50,
                                currency = "EUR",
                                customerName = "Julien Martin",
                                updatedAtIso = "2026-06-18T09:15:00Z",
                            ),
                            Order(
                                id = 3L,
                                reference = "#ORD-8490",
                                status = "Payée",
                                totalPaid = 112.0,
                                currency = "EUR",
                                customerName = "Mme Leblanc",
                                updatedAtIso = "2026-11-02T16:45:00Z",
                            ),
                        ),
                    isLoading = false,
                    isRefreshing = false,
                ),
            onRefresh = {},
            onOrderClick = {},
        )
    }
}
