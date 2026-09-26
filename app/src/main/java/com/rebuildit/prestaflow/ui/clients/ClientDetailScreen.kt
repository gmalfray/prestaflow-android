package com.rebuildit.prestaflow.ui.clients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.core.util.normalizeForMatch
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientOrder
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.NotFoundState
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.components.euroCurrencyFormatter
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ClientDetailRoute(
    onBackClick: () -> Unit,
    onOrderClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ClientDetailScreen(
        modifier = modifier,
        state = state,
        onBackClick = onBackClick,
        onOrderClick = onOrderClick,
        onClearError = viewModel::clearError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    state: ClientDetailUiState,
    onBackClick: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = state.error?.asString()
    val backDesc = stringResource(R.string.content_description_back)

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onClearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(state.client?.fullName ?: stringResource(R.string.client_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backDesc,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.client != null ->
                ClientContent(
                    modifier = Modifier.padding(padding),
                    client = state.client,
                    availableStatuses = state.availableStatuses,
                    onOrderClick = onOrderClick,
                )
            else ->
                NotFoundState(
                    message = stringResource(R.string.client_not_found),
                    modifier = Modifier.padding(padding),
                    onBackClick = onBackClick,
                )
        }
    }
}

/** Nombre de commandes affichées avant de proposer « Voir tout ». */
private const val MAX_ORDERS_COLLAPSED = 10

@Suppress("LongMethod") // Composable contenu client : infos personnelles, adresses et historique commandes
@Composable
private fun ClientContent(
    client: Client,
    availableStatuses: List<OrderStatusFilter>,
    onOrderClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currencyFormatter = remember { euroCurrencyFormatter() }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingM),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
    ) {
        // Client Info Card : avatar + nom + email
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadius),
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(
                modifier = Modifier.padding(Dimensions.cardPadding),
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarInitials(
                    name = client.fullName.ifBlank { client.email },
                    size = 52.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = client.fullName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (client.email.isNotBlank()) {
                        Text(
                            text = client.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Stats Card : total dépensé + nombre de commandes
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadius),
            colors =
                androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.cardPadding),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
            ) {
                Text(
                    text = stringResource(R.string.client_stats_section),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // weight(fill=false) sur les deux colonnes : à fontScale élevé, un montant en
                // headlineMedium peut devenir large — chacune est plafonnée à sa part de largeur
                // au lieu de déborder hors de l'écran.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.client_total_spent_label).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = currencyFormatter.format(client.totalSpent),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.client_orders_count_label).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = client.ordersCount.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Order History
        if (client.orders.isNotEmpty()) {
            Text(
                text = stringResource(R.string.client_order_history_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val displayedOrders =
                if (historyExpanded) client.orders else client.orders.take(MAX_ORDERS_COLLAPSED)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimensions.cardCornerRadius),
                colors =
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column {
                    displayedOrders.forEachIndexed { index, order ->
                        OrderHistoryRow(
                            order = order,
                            availableStatuses = availableStatuses,
                            currencyFormatter = currencyFormatter,
                            dateFormatter = dateFormatter,
                            onClick = { onOrderClick(order.id) },
                        )
                        if (index < displayedOrders.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            )
                        }
                    }
                }
            }

            // Bouton « Voir tout » si l'historique est tronqué
            if (!historyExpanded && client.orders.size > MAX_ORDERS_COLLAPSED) {
                TextButton(
                    onClick = { historyExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.client_show_all_orders, client.orders.size),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryRow(
    order: ClientOrder,
    availableStatuses: List<OrderStatusFilter>,
    currencyFormatter: NumberFormat,
    dateFormatter: DateTimeFormatter,
    onClick: () -> Unit,
) {
    val totalPaid =
        remember(order.totalPaid) {
            currencyFormatter.format(order.totalPaid)
        }
    val dateAdded =
        remember(order.dateAdded) {
            formatTimestamp(order.dateAdded, dateFormatter)
        }
    // Couleur résolue par nom contre la liste des statuts PrestaShop (match normalisé)
    val resolvedStatusColor =
        remember(order.status, availableStatuses) {
            availableStatuses.firstOrNull { it.name.normalizeForMatch() == order.status.normalizeForMatch() }?.color
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // weight(fill=false) sur les deux : même fragilité fontScale que la ligne de liste
            // des commandes (cf. OrdersScreen) — chacun est plafonné à sa part de largeur au lieu
            // de déborder hors écran.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = order.reference,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = totalPaid,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .padding(start = Dimensions.spacingXs),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                if (dateAdded != null) {
                    Text(
                        text = dateAdded,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                OrderStatusBadge(
                    status = order.status,
                    statusColor = resolvedStatusColor,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .padding(start = Dimensions.spacingXs),
                )
            }
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

/**
 * Preuve visuelle du fix fontScale : les stats (total dépensé / commandes) et les lignes
 * d'historique (référence + total, date + badge de statut long) ne doivent pas déborder hors de
 * l'écran à fontScale 1.5 puis 2.0.
 */
@Preview(showBackground = true, name = "Client — détail")
@Preview(showBackground = true, fontScale = 1.5f, name = "Client — détail — fontScale 1.5")
@Preview(showBackground = true, fontScale = 2f, name = "Client — détail — fontScale 2.0")
@Composable
private fun PreviewClientDetail() {
    PrestaFlowTheme {
        ClientContent(
            client =
                Client(
                    id = 1L,
                    firstName = "Camille",
                    lastName = "Martin",
                    email = "camille.martin@example.com",
                    ordersCount = 12,
                    totalSpent = 1234.56,
                    lastOrderAtIso = "2026-06-19T14:20:00Z",
                    orders =
                        listOf(
                            ClientOrder(
                                id = 1L,
                                reference = "FRAYKTSRP",
                                status = "En cours de préparation",
                                totalPaid = 45.0,
                                currency = "EUR",
                                dateAdded = "2026-06-19 14:20:00",
                            ),
                        ),
                ),
            availableStatuses = emptyList(),
            onOrderClick = {},
        )
    }
}
