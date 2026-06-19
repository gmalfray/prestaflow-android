package com.rebuildit.prestaflow.ui.clients

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ClientsRoute(
    onClientClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClientsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ClientsScreen(
        modifier = modifier,
        state = state,
        onRefresh = viewModel::onRefresh,
        onClientClick = onClientClick
    )
}

@Composable
fun ClientsScreen(
    state: ClientsUiState,
    onRefresh: () -> Unit,
    onClientClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.clients.isEmpty() -> LoadingState(modifier)
        state.clients.isEmpty() -> EmptyState(
            message = stringResource(R.string.clients_list_empty),
            modifier = modifier,
            errorMessage = errorMessage,
            onRefresh = onRefresh
        )
        else -> ClientList(
            modifier = modifier,
            clients = state.clients,
            isRefreshing = state.isRefreshing,
            errorMessage = errorMessage,
            onRefresh = onRefresh,
            onClientClick = onClientClick
        )
    }
}

@Composable
private fun ClientList(
    modifier: Modifier,
    clients: List<Client>,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onClientClick: (Long) -> Unit
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }

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
            items(clients, key = { it.id }) { client ->
                ClientCard(
                    client = client,
                    currencyFormatter = currencyFormatter,
                    dateFormatter = dateFormatter,
                    onClick = { onClientClick(client.id) }
                )
            }
        }
    }
}

@Composable
private fun ClientCard(
    client: Client,
    currencyFormatter: NumberFormat,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit
) {
    val totalSpent = remember(client.totalSpent) { currencyFormatter.format(client.totalSpent) }
    val lastOrderAt = remember(client.lastOrderAtIso) {
        formatTimestamp(client.lastOrderAtIso, dateFormatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = client.fullName.ifBlank { client.email },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (client.email.isNotBlank()) {
                Text(
                    text = client.email,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = stringResource(id = R.string.clients_total_spent, totalSpent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stringResource(id = R.string.clients_orders_count, client.ordersCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (lastOrderAt != null) {
                Text(
                    text = stringResource(id = R.string.clients_last_order, lastOrderAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
