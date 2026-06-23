package com.rebuildit.prestaflow.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.print.InvoicePrinter
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OrdersRoute(
    onOrderClick: (Long) -> Unit,
    onAddShop: () -> Unit = {},
    viewModel: OrdersViewModel = hiltViewModel(),
    shopsViewModel: ShopsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val connections by shopsViewModel.connections.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.printError) {
        val err = uiState.printError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.consumePrintError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        OrdersScreen(
            uiState = uiState,
            connections = connections,
            onRefresh = { forceRemote -> viewModel.refresh(forceRemote, notifyOnError = true) },
            onOrderClick = { id ->
                if (uiState.selectionMode) {
                    viewModel.onOrderSelectionToggle(id)
                } else {
                    onOrderClick(id)
                }
            },
            onQueryChange = viewModel::onQueryChange,
            onOrderLongPress = viewModel::onOrderLongPress,
            onCancelSelection = viewModel::cancelSelection,
            onSwitchShop = shopsViewModel::switchShop,
            onAddShop = onAddShop,
            onPrintSelected = {
                viewModel.printSelectedInvoices { pdfList ->
                    val count = uiState.selectedOrderIds.size
                    InvoicePrinter.print(
                        context = context,
                        pdfBytesList = pdfList,
                        jobName = context.getString(R.string.orders_print_job_name, count),
                    )
                }
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Suppress("LongParameterList")
@Composable
fun OrdersScreen(
    uiState: OrdersUiState,
    onRefresh: (Boolean) -> Unit,
    onOrderClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    connections: List<ShopConnection> = emptyList(),
    onQueryChange: (String) -> Unit = {},
    onOrderLongPress: (Long) -> Unit = {},
    onCancelSelection: () -> Unit = {},
    onPrintSelected: () -> Unit = {},
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
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
                isPrintingInProgress = uiState.isPrintingInProgress,
                errorMessage = errorMessage,
                connections = connections,
                onRefresh = { onRefresh(true) },
                onOrderClick = onOrderClick,
                onOrderLongPress = onOrderLongPress,
                selectionMode = uiState.selectionMode,
                selectedOrderIds = uiState.selectedOrderIds,
                onCancelSelection = onCancelSelection,
                onPrintSelected = onPrintSelected,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun OrdersList(
    modifier: Modifier,
    orders: List<Order>,
    totalCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    isRefreshing: Boolean,
    isPrintingInProgress: Boolean,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onOrderLongPress: (Long) -> Unit,
    selectionMode: Boolean,
    selectedOrderIds: Set<Long>,
    onCancelSelection: () -> Unit,
    onPrintSelected: () -> Unit,
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
) {
    val dateFormatter = rememberDateFormatter()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        PullToRefreshBox(
            modifier = Modifier.fillMaxSize(),
            isRefreshing = isRefreshing && !selectionMode,
            onRefresh = { if (!selectionMode) onRefresh() },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barre d'action sélection multiple
                AnimatedVisibility(visible = selectionMode, enter = fadeIn(), exit = fadeOut()) {
                    SelectionActionBar(
                        selectedCount = selectedOrderIds.size,
                        isPrintingInProgress = isPrintingInProgress,
                        onCancel = onCancelSelection,
                        onPrint = onPrintSelected,
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
                    // En-tête : nombre de commandes + sélecteur boutique
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS)) {
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
                            if (connections.isNotEmpty()) {
                                ShopSwitcherChip(
                                    connections = connections,
                                    onSwitch = onSwitchShop,
                                    onAddShop = onAddShop,
                                )
                            }
                        }
                    }

                    // Champ de recherche (masqué en mode sélection pour simplifier l'UX)
                    if (!selectionMode) {
                        item {
                            SearchField(
                                query = query,
                                onQueryChange = onQueryChange,
                                placeholder = stringResource(R.string.orders_search_placeholder),
                            )
                        }
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
                                            selectionMode = selectionMode,
                                            isSelected = order.id in selectedOrderIds,
                                            onClick = { onOrderClick(order.id) },
                                            onLongPress = { onOrderLongPress(order.id) },
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
        } // fin PullToRefreshBox
    }
}

/**
 * Barre contextuelle affichée en haut de l'écran lors de la sélection multiple.
 * Affiche le nombre de commandes sélectionnées, un bouton Annuler et un bouton Imprimer.
 */
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    isPrintingInProgress: Boolean,
    onCancel: () -> Unit,
    onPrint: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin, vertical = Dimensions.spacingS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
            ) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.orders_selection_cancel),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = stringResource(R.string.orders_selection_count, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            if (isPrintingInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimensions.iconSizeMedium),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = onPrint,
                    enabled = selectedCount > 0,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Dimensions.iconSizeSmall),
                    )
                    Spacer(modifier = Modifier.size(Dimensions.spacingXs))
                    Text(
                        text = stringResource(R.string.orders_selection_print, selectedCount),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Ligne de commande — design Stitch : avatar initiales, nom + référence,
 * badge statut coloré, montant aligné à droite.
 *
 * En mode sélection :
 *  - Les commandes avec [Order.hasInvoice] = true sont sélectionnables (clic = toggle).
 *  - Les commandes sans facture sont grisées et non sélectionnables.
 *  - Une coche verte remplace l'avatar pour les commandes sélectionnées.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
@Composable
private fun OrderRow(
    order: Order,
    dateFormatter: DateTimeFormatter,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
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

    // En mode sélection, les commandes sans facture ne sont pas interactives
    val isSelectable = !selectionMode || order.hasInvoice
    val rowAlpha = if (selectionMode && !order.hasInvoice) 0.4f else 1f

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(rowAlpha)
                .then(
                    if (isSelectable) {
                        Modifier.combinedClickable(
                            onClickLabel =
                                if (selectionMode) {
                                    stringResource(R.string.orders_action_toggle_selection)
                                } else {
                                    stringResource(R.string.orders_action_open)
                                },
                            role = Role.Button,
                            onClick = onClick,
                            onLongClick = onLongPress,
                        )
                    } else {
                        Modifier
                    },
                )
                .background(
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        } else {
                            Color.Transparent
                        },
                )
                .padding(Dimensions.cardPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingM),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar ou coche de sélection
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        } else {
            AvatarInitials(
                name = order.customerName.ifBlank { order.reference },
            )
        }

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
                                hasInvoice = true,
                            ),
                            Order(
                                id = 2L,
                                reference = "#ORD-8491",
                                status = "Expédiée",
                                totalPaid = 28.50,
                                currency = "EUR",
                                customerName = "Julien Martin",
                                updatedAtIso = "2026-06-18T09:15:00Z",
                                hasInvoice = false,
                            ),
                            Order(
                                id = 3L,
                                reference = "#ORD-8490",
                                status = "Payée",
                                totalPaid = 112.0,
                                currency = "EUR",
                                customerName = "Mme Leblanc",
                                updatedAtIso = "2026-11-02T16:45:00Z",
                                hasInvoice = true,
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

@Preview(showBackground = true, name = "Commandes — mode sélection")
@Composable
private fun PreviewOrdersListSelection() {
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
                                hasInvoice = true,
                            ),
                            Order(
                                id = 2L,
                                reference = "#ORD-8491",
                                status = "Expédiée",
                                totalPaid = 28.50,
                                currency = "EUR",
                                customerName = "Julien Martin",
                                updatedAtIso = "2026-06-18T09:15:00Z",
                                hasInvoice = false,
                            ),
                        ),
                    isLoading = false,
                    isRefreshing = false,
                    selectionMode = true,
                    selectedOrderIds = setOf(1L),
                ),
            onRefresh = {},
            onOrderClick = {},
        )
    }
}
