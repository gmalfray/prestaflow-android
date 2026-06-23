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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.SectionHeader
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
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
    CartsScreen(
        modifier = modifier,
        state = state,
        connections = connections,
        onRefresh = viewModel::onRefresh,
        onCartClick = onCartClick,
        onSwitchShop = shopsViewModel::switchShop,
        onAddShop = onAddShop,
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
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.carts.isEmpty() -> LoadingState(modifier)
        state.carts.isEmpty() ->
            EmptyState(
                message = stringResource(R.string.carts_list_empty),
                modifier = modifier,
                errorMessage = errorMessage,
                onRefresh = onRefresh,
            )
        else ->
            CartsList(
                modifier = modifier,
                carts = state.carts,
                isRefreshing = state.isRefreshing,
                errorMessage = errorMessage,
                connections = connections,
                onRefresh = onRefresh,
                onCartClick = onCartClick,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun CartsList(
    modifier: Modifier,
    carts: List<CartSummary>,
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onCartClick: (Int) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
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
                                carts.forEachIndexed { index, cart ->
                                    CartRow(cart = cart, onClick = { onCartClick(cart.id) })
                                    if (index < carts.lastIndex) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = updatedAt ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cart.hasOrder) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
