package com.rebuildit.prestaflow.ui.carts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CartsRoute(
    onCartClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CartsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CartsScreen(
        modifier = modifier,
        state = state,
        onRefresh = viewModel::onRefresh,
        onCartClick = onCartClick
    )
}

@Composable
fun CartsScreen(
    state: CartsUiState,
    onRefresh: () -> Unit,
    onCartClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.carts.isEmpty() -> LoadingState(modifier)
        state.carts.isEmpty() -> EmptyState(
            message = stringResource(R.string.carts_list_empty),
            modifier = modifier,
            errorMessage = errorMessage,
            onRefresh = onRefresh
        )
        else -> CartsList(
            modifier = modifier,
            carts = state.carts,
            isRefreshing = state.isRefreshing,
            errorMessage = errorMessage,
            onRefresh = onRefresh,
            onCartClick = onCartClick
        )
    }
}

@Composable
private fun CartsList(
    modifier: Modifier,
    carts: List<CartSummary>,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onCartClick: (Int) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isRefreshing, enter = fadeIn(), exit = fadeOut()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (errorMessage != null) {
            ErrorRow(message = errorMessage, onRefresh = onRefresh)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(carts, key = { it.id }) { cart ->
                CartCard(cart = cart, onClick = { onCartClick(cart.id) })
            }
        }
    }
}

@Composable
private fun CartCard(
    cart: CartSummary,
    onClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val totalText = remember(cart.totalTaxIncl, cart.currencyIso) {
        formatCurrency(cart.totalTaxIncl, cart.currencyIso)
    }
    val updatedAt = remember(cart.updatedAtIso) {
        formatTimestamp(cart.updatedAtIso, dateFormatter)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cart.customerName.ifBlank { stringResource(R.string.carts_customer_guest) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.carts_items_count, cart.itemsCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cart.hasOrder) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.carts_has_order),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                } else {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = stringResource(R.string.carts_abandoned),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (updatedAt != null) {
                Text(
                    text = updatedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
