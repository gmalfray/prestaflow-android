package com.rebuildit.prestaflow.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    // Filet de sécurité : ré-applique le filtre de navigation quand le SavedStateHandle
    // est mis à jour par Navigation (cas launchSingleTop + ViewModel réutilisé depuis
    // la back-stack sauvegardée). Sans ce LaunchedEffect, un ViewModel restauré via
    // restoreState resterait dans son ancien mode (TOP_CLIENTS) malgré filter=new.
    val filterArg by viewModel.navigationFilterFlow.collectAsStateWithLifecycle()
    LaunchedEffect(filterArg) {
        val targetFilter = when (filterArg) {
            "new" -> ClientFilter.NEW_THIS_MONTH
            else -> null // null = pas de forçage (le mode TOP par défaut est géré par l'init du VM)
        }
        targetFilter?.let { viewModel.onNavigationFilter(it) }
    }

    ClientsScreen(
        modifier = modifier,
        state = state,
        connections = connections,
        onRefresh = viewModel::onRefresh,
        onClientClick = onClientClick,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onLoadMore = viewModel::onLoadMore,
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
    onFilterChange: (ClientFilter) -> Unit = {},
    onLoadMore: () -> Unit = {},
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
                activeFilter = state.activeFilter,
                listMode = state.listMode,
                query = state.query,
                onQueryChange = onQueryChange,
                onFilterChange = onFilterChange,
                hasNextPage = state.hasNextPage,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = onLoadMore,
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
    activeFilter: ClientFilter,
    listMode: ClientListMode,
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ClientFilter) -> Unit,
    hasNextPage: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    isRefreshing: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onClientClick: (Long) -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
) {
    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }

    val sectionTitle =
        when (listMode) {
            ClientListMode.TOP -> stringResource(R.string.clients_list_section_title)
            ClientListMode.ALL -> stringResource(R.string.clients_list_section_all)
            ClientListMode.NEW -> stringResource(R.string.clients_list_section_new)
            ClientListMode.SEARCH -> stringResource(R.string.clients_list_section_search)
        }

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
                    // KPI stats : total clients + nouveaux ce mois (cartes cliquables)
                    item {
                        ClientsStatsRow(
                            totalClients = totalClients,
                            newThisMonth = newThisMonth,
                            activeFilter = activeFilter,
                            onFilterChange = onFilterChange,
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

                    // En-tête section (titre dynamique selon le mode)
                    item {
                        SectionHeader(title = sectionTitle)
                    }

                    // Champ de recherche (pleine base, debounce 300ms)
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

                        // Bouton "charger plus" pour les modes paginés (ALL / NEW / SEARCH)
                        if (listMode != ClientListMode.TOP && hasNextPage) {
                            item {
                                LoadMoreButton(
                                    isLoading = isLoadingMore,
                                    onClick = onLoadMore,
                                )
                            }
                        }
                    }
                }
            } // fin Column
        } // fin PullToRefreshBox
    }
}

// ─── Bouton charger plus ──────────────────────────────────────────────────────

@Composable
private fun LoadMoreButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(Dimensions.spacingM),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(
                onClick = onClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.clients_load_more),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ─── KPI stats clients ────────────────────────────────────────────────────────

@Composable
private fun ClientsStatsRow(
    totalClients: Int,
    newThisMonth: Int,
    activeFilter: ClientFilter,
    onFilterChange: (ClientFilter) -> Unit,
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
            selected = activeFilter == ClientFilter.ALL_CLIENTS,
            onClick = { onFilterChange(ClientFilter.ALL_CLIENTS) },
        )
        ClientStatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.clients_stats_new_month),
            value = newThisMonth.toString(),
            selected = activeFilter == ClientFilter.NEW_THIS_MONTH,
            onClick = { onFilterChange(ClientFilter.NEW_THIS_MONTH) },
        )
    }
}

/**
 * Carte KPI de l'écran Clients.
 *
 * [selected] : carte mise en évidence (fond primaryContainer) quand son mode est actif.
 * [onClick]  : callback de sélection/désélection du mode.
 */
@Composable
private fun ClientStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val labelColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                    )
                    if (selected) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.clients_filter_dismiss),
                            tint = labelColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                )
            }
        }
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(Dimensions.cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor,
                )
            }
        }
    }
}

// ─── Ligne client ─────────────────────────────────────────────────────────────

@Composable
private fun ClientRow(
    client: Client,
    currencyFormatter: NumberFormat,
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

@Preview(showBackground = true, name = "Clients — liste top (aucune carte active)")
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
                    stats = com.rebuildit.prestaflow.domain.clients.model.ClientStats(total = 4384, newThisMonth = 42),
                    isLoading = false,
                    isRefreshing = false,
                    activeFilter = ClientFilter.TOP_CLIENTS,
                ),
            onRefresh = {},
            onClientClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Clients — mode Tous les clients (pagination)")
@Composable
private fun PreviewClientsAllMode() {
    PrestaFlowTheme {
        ClientsScreen(
            state =
                ClientsUiState(
                    clients =
                        listOf(
                            Client(
                                id = 1L,
                                firstName = "Alice",
                                lastName = "Bernard",
                                email = "alice@test.fr",
                                ordersCount = 3,
                                totalSpent = 75.0,
                                lastOrderAtIso = null,
                            ),
                        ),
                    stats = com.rebuildit.prestaflow.domain.clients.model.ClientStats(total = 4384, newThisMonth = 42),
                    isLoading = false,
                    isRefreshing = false,
                    activeFilter = ClientFilter.ALL_CLIENTS,
                    hasNextPage = true,
                ),
            onRefresh = {},
            onClientClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Clients — mode Nouveaux du mois actif")
@Composable
private fun PreviewClientsNewMode() {
    PrestaFlowTheme {
        ClientsScreen(
            state =
                ClientsUiState(
                    clients =
                        listOf(
                            Client(
                                id = 1L,
                                firstName = "Alice",
                                lastName = "Bernard",
                                email = "alice@test.fr",
                                ordersCount = 3,
                                totalSpent = 75.0,
                                lastOrderAtIso = null,
                            ),
                        ),
                    stats = com.rebuildit.prestaflow.domain.clients.model.ClientStats(total = 4384, newThisMonth = 42),
                    isLoading = false,
                    isRefreshing = false,
                    activeFilter = ClientFilter.NEW_THIS_MONTH,
                ),
            onRefresh = {},
            onClientClick = {},
        )
    }
}
