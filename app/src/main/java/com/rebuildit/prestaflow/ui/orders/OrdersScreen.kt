package com.rebuildit.prestaflow.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.orders.model.Order
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@Composable
fun OrdersRoute(
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OrdersScreen(
        modifier = modifier,
        state = state,
        onRefresh = viewModel::onRefresh
    )
}

@Composable
fun OrdersScreen(
    state: OrdersUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.orders.isEmpty() -> LoadingState(modifier)
        state.orders.isEmpty() -> EmptyState(modifier, errorMessage, onRefresh)
        else -> OrdersList(
            modifier = modifier,
            orders = state.orders,
            isRefreshing = state.isRefreshing,
            errorMessage = errorMessage,
            onRefresh = onRefresh
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier,
    errorMessage: String?,
    onRefresh: () -> Unit
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(id = R.string.orders_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun OrdersList(
    modifier: Modifier,
    orders: List<Order>,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit
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
                    dateFormatter = dateFormatter
                )
            }
        }
    }
}

@Composable
private fun ErrorRow(message: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    dateFormatter: DateTimeFormatter
) {
    val amountText = remember(order.totalPaid, order.currency) {
        formatCurrency(order.totalPaid, order.currency)
    }
    val updatedAt = remember(order.updatedAtIso) {
        formatTimestamp(order.updatedAtIso, dateFormatter)
    }

    Card(
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

private fun formatCurrency(amount: Double, currencyCode: String): String {
    val formatter = NumberFormat.getCurrencyInstance()
    runCatching { formatter.currency = Currency.getInstance(currencyCode) }
    return formatter.format(amount)
}

private fun formatTimestamp(value: String, formatter: DateTimeFormatter): String {
    val zone = ZoneId.systemDefault()

    val fromInstant = runCatching {
        Instant.parse(value)
    }.map { instant -> instant.atZone(zone).format(formatter) }

    if (fromInstant.isSuccess) {
        return fromInstant.getOrThrow()
    }

    val localPatterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    localPatterns.forEach { pattern ->
        runCatching {
            LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern))
        }.map { localDateTime ->
            return localDateTime.atZone(zone).format(formatter)
        }
    }

    return value
}
