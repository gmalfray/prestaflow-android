package com.rebuildit.prestaflow.ui.carts

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
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.components.SectionHeader
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CartsRoute(
    onCartClick: (Int) -> Unit = {},
    onAddShop: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CartsViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()

    // Rattrapage : recharge la liste quand l'écran redevient visible (retour d'un autre onglet),
    // cf. KDoc de OrdersViewModel.onScreenResumed pour le throttle et le pourquoi.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed()
    }

    CartsScreen(
        modifier = modifier,
        state = state,
        connections = connections,
        onRefresh = viewModel::onRefresh,
        onCartClick = onCartClick,
        onSwitchShop = shopsViewModel::switchShop,
        onAddShop = onAddShop,
        onQueryChanged = viewModel::onQueryChanged,
        onLoadMore = viewModel::loadMore,
    )
}

@Composable
fun CartsScreen(
    state: CartsUiState,
    onRefresh: () -> Unit,
    onCartClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    connections: List<ShopConnection> = emptyList(),
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.allCarts.isEmpty() -> LoadingState(modifier)
        state.allCarts.isEmpty() ->
            EmptyState(
                message = stringResource(R.string.carts_list_empty),
                modifier = modifier,
                errorMessage = errorMessage,
                onRefresh = onRefresh,
            )
        else ->
            CartsList(
                modifier = modifier,
                state = state,
                isRefreshing = state.isRefreshing,
                errorMessage = errorMessage,
                connections = connections,
                onRefresh = onRefresh,
                onCartClick = onCartClick,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
                onQueryChanged = onQueryChanged,
                onLoadMore = onLoadMore,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun CartsList(
    modifier: Modifier,
    state: CartsUiState,
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onCartClick: (Int) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
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
                    // Sélecteur de boutique
                    if (connections.isNotEmpty()) {
                        item {
                            ShopSwitcherChip(
                                connections = connections,
                                onSwitch = onSwitchShop,
                                onAddShop = onAddShop,
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.carts_list_abandoned),
                        )
                    }

                    // Champ de recherche (filtre local par nom client)
                    item {
                        SearchField(
                            query = state.query,
                            onQueryChange = onQueryChanged,
                            placeholder = stringResource(R.string.carts_search_placeholder),
                        )
                    }

                    if (state.carts.isEmpty() && state.query.isNotBlank()) {
                        item {
                            Text(
                                text = stringResource(R.string.list_no_results, state.query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Dimensions.spacingM),
                            )
                        }
                    } else if (state.carts.isNotEmpty()) {
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
                                    state.carts.forEachIndexed { index, cart ->
                                        CartRow(cart = cart, onClick = { onCartClick(cart.id) })
                                        if (index < state.carts.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                thickness = 1.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Bouton « Charger plus » si pagination disponible
                        if (state.hasMore) {
                            item {
                                OutlinedButton(
                                    onClick = onLoadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(text = stringResource(R.string.carts_load_more))
                                }
                            }
                        }
                    }
                }
            }
        } // fin PullToRefreshBox
    }
}

@Composable
private fun CartRow(
    cart: CartSummary,
    onClick: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val totalText =
        remember(cart.totalTaxIncl, cart.currencyIso) {
            formatCurrency(cart.totalTaxIncl, cart.currencyIso)
        }
    val updatedAt =
        remember(cart.updatedAtIso) {
            formatTimestamp(cart.updatedAtIso, dateFormatter)
        }
    val displayName = cart.customerName.ifBlank { stringResource(R.string.carts_customer_guest) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarInitials(name = displayName)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            // weight(fill=false) sur les deux : le libellé du badge (« Converti en commande »,
            // « Panier abandonné ») est une phrase complète qui peut devenir large à fontScale
            // élevé — même fragilité que OrderStatusBadge, corrigée à l'identique.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = updatedAt ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (cart.hasOrder) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f, fill = false).padding(start = Dimensions.spacingXs),
                    ) {
                        Text(
                            text = stringResource(R.string.carts_has_order),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f, fill = false).padding(start = Dimensions.spacingXs),
                    ) {
                        Text(
                            text = stringResource(R.string.carts_abandoned),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

/**
 * Preuve visuelle du fix fontScale : le badge (« Converti en commande » / « Panier abandonné »),
 * phrase complète, ne doit pas déborder hors de l'écran à côté de la date à fontScale 1.5/2.0.
 */
@Preview(showBackground = true, name = "Paniers — liste")
@Preview(showBackground = true, fontScale = 1.5f, name = "Paniers — liste — fontScale 1.5")
@Preview(showBackground = true, fontScale = 2f, name = "Paniers — liste — fontScale 2.0")
@Composable
private fun PreviewCartsList() {
    PrestaFlowTheme {
        CartsScreen(
            state =
                CartsUiState(
                    allCarts =
                        listOf(
                            CartSummary(
                                id = 1,
                                customerName = "Camille Martin",
                                customerEmail = "camille@example.com",
                                currencyIso = "EUR",
                                totalTaxIncl = 1234.56,
                                itemsCount = 3,
                                hasOrder = true,
                                createdAtIso = "2026-06-19T14:20:00Z",
                                updatedAtIso = "2026-06-19T14:20:00Z",
                            ),
                            CartSummary(
                                id = 2,
                                customerName = "Julien Martin",
                                customerEmail = null,
                                currencyIso = "EUR",
                                totalTaxIncl = 28.50,
                                itemsCount = 1,
                                hasOrder = false,
                                createdAtIso = "2026-06-18T09:15:00Z",
                                updatedAtIso = "2026-06-18T09:15:00Z",
                            ),
                        ),
                    isLoading = false,
                    isRefreshing = false,
                ),
            onRefresh = {},
            onCartClick = {},
        )
    }
}
