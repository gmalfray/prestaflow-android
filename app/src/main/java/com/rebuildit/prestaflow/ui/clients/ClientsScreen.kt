package com.rebuildit.prestaflow.ui.clients

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.components.SectionHeader
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ClientsRoute(
    onClientClick: (Long) -> Unit = {},
    onAddShop: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClientsViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()
    ClientsScreen(
        modifier = modifier,
        state = state,
        connections = connections,
        onRefresh = viewModel::onRefresh,
        onClientClick = onClientClick,
        onQueryChange = viewModel::onQueryChange,
        onSwitchShop = shopsViewModel::switchShop,
        onAddShop = onAddShop,
    )
}

@Composable
fun ClientsScreen(
    state: ClientsUiState,
    onRefresh: () -> Unit,
    onClientClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    connections: List<ShopConnection> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
) {
    val errorMessage = state.error?.asString()

    when {
        state.isLoading && state.clients.isEmpty() -> LoadingState(modifier)
        state.clients.isEmpty() ->
            EmptyState(
                message = stringResource(R.string.clients_list_empty),
                modifier = modifier,
                errorMessage = errorMessage,
                onRefresh = onRefresh,
            )
        else ->
            ClientList(
                modifier = modifier,
                clients = state.visibleClients,
                totalClients = state.stats?.total ?: state.clients.size,
                newThisMonth = state.stats?.newThisMonth ?: 0,
                query = state.query,
                onQueryChange = onQueryChange,
                isRefreshing = state.isRefreshing,
                errorMessage = errorMessage,
                connections = connections,
                onRefresh = onRefresh,
                onClientClick = onClientClick,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun ClientList(
    modifier: Modifier,
    clients: List<Client>,
    totalClients: Int,
    newThisMonth: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onClientClick: (Long) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }

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
                    // KPI stats : total clients + nouveaux ce mois
                    item {
                        ClientsStatsRow(
                            totalClients = totalClients,
                            newThisMonth = newThisMonth,
                        )
                    }

                    item { Spacer(modifier = Modifier.height(Dimensions.spacingXs)) }

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

                    // En-tête section
                    item {
                        SectionHeader(
                            title = stringResource(R.string.clients_list_section_title),
                        )
                    }

                    // Champ de recherche
                    item {
                        SearchField(
                            query = query,
                            onQueryChange = onQueryChange,
                            placeholder = stringResource(R.string.clients_search_placeholder),
                        )
                    }

                    if (clients.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.list_no_results, query),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Dimensions.spacingM),
                            )
                        }
                    } else {
                        // Carte conteneur avec toutes les lignes clients
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
                                    clients.forEachIndexed { index, client ->
                                        ClientRow(
                                            client = client,
                                            currencyFormatter = currencyFormatter,
                                            dateFormatter = dateFormatter,
                                            onClick = { onClientClick(client.id) },
                                        )
                                        if (index < clients.lastIndex) {
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
            } // fin Column
        } // fin PullToRefreshBox
    }
}

// ─── KPI stats clients ────────────────────────────────────────────────────────

@Composable
private fun ClientsStatsRow(
    totalClients: Int,
    newThisMonth: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.gutter),
    ) {
        ClientStatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.clients_stats_total),
            value = totalClients.toString(),
        )
        ClientStatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.clients_stats_new_month),
            value = newThisMonth.toString(),
        )
    }
}

@Composable
private fun ClientStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.cardPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── Ligne client ─────────────────────────────────────────────────────────────

@Composable
private fun ClientRow(
    client: Client,
    currencyFormatter: NumberFormat,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val displayName = client.fullName.ifBlank { client.email }
    val totalSpent = remember(client.totalSpent) { currencyFormatter.format(client.totalSpent) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(R.string.clients_action_open),
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { role = Role.Button }
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar initiales
        AvatarInitials(name = displayName)

        // Contenu central
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (client.email.isNotBlank() && displayName != client.email) {
                Text(
                    text = client.email,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Nombre de commandes à droite
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.clients_orders_count, client.ordersCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = totalSpent,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Clients — liste")
@Composable
private fun PreviewClientsList() {
    PrestaFlowTheme {
        ClientsScreen(
            state =
                ClientsUiState(
                    clients =
                        listOf(
                            Client(
                                id = 1L,
                                firstName = "Marie",
                                lastName = "Lefebvre",
                                email = "marie.lefebvre@gmail.com",
                                ordersCount = 18,
                                totalSpent = 342.0,
                                lastOrderAtIso = null,
                            ),
                            Client(
                                id = 2L,
                                firstName = "Thomas",
                                lastName = "Dubois",
                                email = "thomas.dubois@example.com",
                                ordersCount = 12,
                                totalSpent = 198.50,
                                lastOrderAtIso = null,
                            ),
                        ),
                    isLoading = false,
                    isRefreshing = false,
                ),
            onRefresh = {},
            onClientClick = {},
        )
    }
}
