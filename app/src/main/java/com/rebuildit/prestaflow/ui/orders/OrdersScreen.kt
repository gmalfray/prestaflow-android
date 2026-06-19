package com.rebuildit.prestaflow.ui.orders

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OrdersRoute(
    onOrderClick: (Long) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    OrdersScreen(
        uiState = uiState,
        onRefresh = { forceRemote -> viewModel.refresh(forceRemote, notifyOnError = true) },
        onOrderClick = onOrderClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    uiState: OrdersUiState,
    onRefresh: (Boolean) -> Unit,
    onOrderClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = uiState.error?.asString()

    when {
        uiState.isLoading && uiState.orders.isEmpty() -> LoadingState(modifier)
        uiState.orders.isEmpty() -> EmptyState(
            message = stringResource(R.string.orders_list_empty),
            modifier = modifier,
            errorMessage = errorMessage,
            onRefresh = { onRefresh(true) }
        )
        else -> OrdersList(
            modifier = modifier,
            orders = uiState.orders,
            isRefreshing = uiState.isRefreshing,
            errorMessage = errorMessage,
            onRefresh = { onRefresh(true) },
            onOrderClick = onOrderClick
        )
    }
}

@Suppress("LongParameterList") // Composable liste : modifier + data + états + callbacks requis par l'architecture screen/content
@Composable
private fun OrdersList(
    modifier: Modifier,
    orders: List<Order>,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOrderClick: (Long) -> Unit
) {
    val dateFormatter = rememberDateFormatter()

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
            items(orders, key = { it.id }) { order ->
                OrderCard(
                    order = order,
                    onClick = { onOrderClick(order.id) }
                )
            }
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    onClick: () -> Unit
) {
    val dateFormatter = rememberDateFormatter()
    val amountText = remember(order.totalPaid, order.currency) {
        formatCurrency(order.totalPaid, order.currency)
    }
    val updatedAt = remember(order.updatedAtIso) {
        formatTimestamp(order.updatedAtIso, dateFormatter) ?: order.updatedAtIso
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = order.reference,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = order.status.ifBlank { stringResource(id = R.string.orders_status_unknown) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = order.customerName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(id = R.string.orders_last_updated, updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun rememberDateFormatter(): DateTimeFormatter {
    return remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    }
}
