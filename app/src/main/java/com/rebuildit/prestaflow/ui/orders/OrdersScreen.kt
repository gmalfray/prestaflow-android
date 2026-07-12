package com.rebuildit.prestaflow.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.core.print.InvoicePrinter
import com.rebuildit.prestaflow.core.ui.asString
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.ui.components.AvatarInitials
import com.rebuildit.prestaflow.ui.components.EmptyState
import com.rebuildit.prestaflow.ui.components.ErrorRow
import com.rebuildit.prestaflow.ui.components.LoadingState
import com.rebuildit.prestaflow.ui.components.OrderStatusBadge
import com.rebuildit.prestaflow.ui.components.SearchField
import com.rebuildit.prestaflow.ui.components.ShopSwitcherChip
import com.rebuildit.prestaflow.ui.components.contrastTextColor
import com.rebuildit.prestaflow.ui.components.formatCurrency
import com.rebuildit.prestaflow.ui.components.formatTimestamp
import com.rebuildit.prestaflow.ui.components.parseHexColor
import com.rebuildit.prestaflow.ui.dashboard.labelRes
import com.rebuildit.prestaflow.ui.orders.components.StatusPickerDialog
import com.rebuildit.prestaflow.ui.settings.ShopsViewModel
import com.rebuildit.prestaflow.ui.theme.Dimensions
import com.rebuildit.prestaflow.ui.theme.PrestaFlowTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

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
    var showPrintModeDialog by remember { mutableStateOf(false) }
    var showBulkStatusDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.printError) {
        val err = uiState.printError
        if (err != null) {
            snackbarHostState.showSnackbar(err)
            viewModel.consumePrintError()
        }
    }

    LaunchedEffect(uiState.bulkSnackbar) {
        val msg = uiState.bulkSnackbar
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeBulkSnackbar()
        }
    }

    if (showPrintModeDialog) {
        PrintModeDialog(
            onDismiss = { showPrintModeDialog = false },
            onModeSelected = { mode ->
                showPrintModeDialog = false
                viewModel.printSelectedInvoices { pdfList ->
                    val count = uiState.selectedOrderIds.size
                    InvoicePrinter.print(
                        context = context,
                        pdfBytesList = pdfList,
                        jobName = context.getString(R.string.orders_print_job_name, count),
                        mode = mode,
                    )
                }
            },
        )
    }

    if (showBulkStatusDialog) {
        StatusPickerDialog(
            statuses = uiState.availableStatuses,
            currentStatusId = null,
            onConfirm = { statusId ->
                showBulkStatusDialog = false
                viewModel.bulkUpdateStatus(statusId)
            },
            onDismiss = { showBulkStatusDialog = false },
        )
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
            onStatusFilterSelected = viewModel::onStatusFilterSelected,
            onStatusFiltersReplaced = viewModel::onStatusFiltersReplaced,
            onVisibleStatusIdsChanged = viewModel::onVisibleStatusIdsChanged,
            onPrintSelected = { showPrintModeDialog = true },
            onBulkChangeStatus = { showBulkStatusDialog = true },
            onSortChanged = viewModel::onSortChanged,
            onLoadMore = viewModel::loadMore,
            onSwipeAction = viewModel::onSwipeAction,
            swipeConfig = uiState.swipeConfig,
            onClearPeriodFilter = viewModel::clearPeriodFilter,
        )
        // Barre d'annulation swipe (avec décompte vivant) + snackbars classiques, empilées en bas
        // pour ne jamais se chevaucher. La barre swipe n'utilise PAS le SnackbarHostState : ce
        // dernier ne permet pas de mettre à jour le texte d'un snackbar déjà affiché, incompatible
        // avec un décompte de secondes qui doit se rafraîchir chaque seconde.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
            uiState.pendingSwipeAction?.let { action ->
                SwipeUndoBar(
                    action = action,
                    onCancel = viewModel::cancelSwipeAction,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimensions.screenEdgeMargin),
                )
            }
            SnackbarHost(hostState = snackbarHostState)
        }
    }
}

/**
 * Barre d'annulation du swipe de changement de statut — remplace l'ancien Snackbar à durée
 * indéfinie par un composant dédié entièrement piloté par l'état Compose, seule façon d'afficher
 * un décompte vivant des secondes restantes avant l'envoi effectif (l'API Snackbar ne permet pas
 * de mettre à jour le texte d'un snackbar déjà affiché).
 *
 * Le décompte redémarre à [SWIPE_UNDO_DELAY_MS] / 1000 secondes à chaque nouvelle [action] (chaque
 * swipe annule et remplace le précédent côté ViewModel) et disparaît dès que [action] devient null
 * (annulation via le bouton, ou envoi effectif une fois le délai écoulé).
 */
@Composable
private fun SwipeUndoBar(
    action: PendingSwipeAction,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSeconds = (SWIPE_UNDO_DELAY_MS / 1_000L).toInt()
    var remainingSeconds by remember(action) { mutableIntStateOf(totalSeconds) }

    // Décompte affiché : purement visuel, resynchronisé à chaque nouvelle action. L'envoi effectif
    // reste piloté côté ViewModel par son propre delay(SWIPE_UNDO_DELAY_MS), indépendant de ce timer UI.
    LaunchedEffect(action) {
        for (secondsLeft in totalSeconds - 1 downTo 0) {
            delay(1_000L)
            remainingSeconds = secondsLeft
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Dimensions.cardCornerRadius),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.spacingM, vertical = Dimensions.spacingS),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        R.string.orders_swipe_pending,
                        action.orderReference,
                        action.targetStatusName,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.orders_swipe_undo_countdown, remainingSeconds),
                    color = MaterialTheme.colorScheme.inversePrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
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
    onBulkChangeStatus: () -> Unit = {},
    onSwitchShop: (String) -> Unit = {},
    onAddShop: () -> Unit = {},
    onStatusFilterSelected: (Int?) -> Unit = {},
    onStatusFiltersReplaced: (Set<Int>) -> Unit = {},
    onVisibleStatusIdsChanged: (Set<Int>) -> Unit = {},
    onSortChanged: (OrderSort) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSwipeAction: (Long, String, SwipeDirection) -> Unit = { _, _, _ -> },
    swipeConfig: SwipeConfig = SwipeConfig(),
    onClearPeriodFilter: () -> Unit = {},
) {
    val errorMessage = uiState.error?.asString()

    when {
        uiState.isLoading && uiState.orders.isEmpty() && !uiState.hasActiveStatusFilter ->
            LoadingState(modifier)
        uiState.orders.isEmpty() && !uiState.hasActiveStatusFilter ->
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
                isBulkUpdating = uiState.isBulkUpdating,
                errorMessage = errorMessage,
                connections = connections,
                onRefresh = { onRefresh(true) },
                onOrderClick = onOrderClick,
                onOrderLongPress = onOrderLongPress,
                selectionMode = uiState.selectionMode,
                selectedOrderIds = uiState.selectedOrderIds,
                onCancelSelection = onCancelSelection,
                onPrintSelected = onPrintSelected,
                onBulkChangeStatus = onBulkChangeStatus,
                onSwitchShop = onSwitchShop,
                onAddShop = onAddShop,
                availableStatuses = uiState.availableStatuses,
                filteredStatuses = uiState.filteredStatuses,
                visibleStatusIds = uiState.visibleStatusIds,
                selectedStatusIds = uiState.selectedStatusIds,
                onStatusFilterSelected = onStatusFilterSelected,
                onStatusFiltersReplaced = onStatusFiltersReplaced,
                onVisibleStatusIdsChanged = onVisibleStatusIdsChanged,
                selectedSort = uiState.selectedSort,
                onSortChanged = onSortChanged,
                hasMore = uiState.hasMore,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = onLoadMore,
                onSwipeAction = onSwipeAction,
                swipeConfig = swipeConfig,
                activePeriod = uiState.activePeriod,
                onClearPeriodFilter = onClearPeriodFilter,
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
    isBulkUpdating: Boolean = false,
    errorMessage: String?,
    connections: List<ShopConnection>,
    onRefresh: () -> Unit,
    onOrderClick: (Long) -> Unit,
    onOrderLongPress: (Long) -> Unit,
    selectionMode: Boolean,
    selectedOrderIds: Set<Long>,
    onCancelSelection: () -> Unit,
    onPrintSelected: () -> Unit,
    onBulkChangeStatus: () -> Unit = {},
    onSwitchShop: (String) -> Unit,
    onAddShop: () -> Unit,
    availableStatuses: List<OrderStatusFilter> = emptyList(),
    filteredStatuses: List<OrderStatusFilter> = emptyList(),
    visibleStatusIds: Set<Int>? = null,
    selectedStatusIds: Set<Int> = emptySet(),
    onStatusFilterSelected: (Int?) -> Unit = {},
    onStatusFiltersReplaced: (Set<Int>) -> Unit = {},
    onVisibleStatusIdsChanged: (Set<Int>) -> Unit = {},
    selectedSort: OrderSort = OrderSort.DATE_DESC,
    onSortChanged: (OrderSort) -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSwipeAction: (Long, String, SwipeDirection) -> Unit = { _, _, _ -> },
    swipeConfig: SwipeConfig = SwipeConfig(),
    activePeriod: DashboardPeriod? = null,
    onClearPeriodFilter: () -> Unit = {},
) {
    val dateFormatter = rememberDateFormatter()
    var showStatusPrefsSheet by rememberSaveable { mutableStateOf(false) }
    val statusPrefsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showStatusPrefsSheet && availableStatuses.isNotEmpty()) {
        StatusPreferencesSheet(
            sheetState = statusPrefsSheetState,
            availableStatuses = availableStatuses,
            selectedStatusIds = selectedStatusIds,
            visibleStatusIds = visibleStatusIds,
            onDismiss = { showStatusPrefsSheet = false },
            onConfirm = { filterIds, shortcutIds ->
                onStatusFiltersReplaced(filterIds)
                onVisibleStatusIdsChanged(shortcutIds)
                showStatusPrefsSheet = false
            },
        )
    }

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
                        isBulkUpdating = isBulkUpdating,
                        onCancel = onCancelSelection,
                        onPrint = onPrintSelected,
                        onChangeStatus = onBulkChangeStatus,
                        hasStatuses = availableStatuses.isNotEmpty(),
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

                        // Chip de filtre période (visible si l'écran a été ouvert depuis le dashboard)
                        if (activePeriod != null) {
                            item {
                                PeriodFilterChip(
                                    period = activePeriod,
                                    onClear = onClearPeriodFilter,
                                )
                            }
                        }

                        // Barre de filtres par statut + tri
                        if (availableStatuses.isNotEmpty()) {
                            item {
                                StatusFilterBar(
                                    statuses = filteredStatuses,
                                    selectedStatusIds = selectedStatusIds,
                                    onStatusToggle = { id -> onStatusFilterSelected(id) },
                                    onConfigureClick = { showStatusPrefsSheet = true },
                                    // Vrai si un filtre porte sur un statut absent des chips (filtré via le menu)
                                    hasHiddenActiveFilter =
                                        selectedStatusIds.isNotEmpty() &&
                                            selectedStatusIds.any { id -> filteredStatuses.none { it.id == id } },
                                    selectedSort = selectedSort,
                                    onSortChanged = onSortChanged,
                                )
                            }
                        }
                    }

                    if (orders.isEmpty()) {
                        // Filtre statut actif sans résultat → bouton de réinitialisation
                        // Affiché dès qu'un filtre de statut est actif, même si une recherche est en cours
                        if (selectedStatusIds.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.padding(vertical = Dimensions.spacingM),
                                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
                                ) {
                                    Text(
                                        text = stringResource(R.string.orders_filter_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = { onStatusFilterSelected(null) }) {
                                        Text(
                                            text = stringResource(R.string.orders_filter_reset),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = stringResource(R.string.list_no_results, query),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = Dimensions.spacingM),
                                )
                            }
                        }
                    } else {
                        // Carte conteneur groupée — chaque ligne peut être swipée
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
                                        key(order.id) {
                                            SwipeableOrderRow(
                                                order = order,
                                                dateFormatter = dateFormatter,
                                                selectionMode = selectionMode,
                                                isSelected = order.id in selectedOrderIds,
                                                onClick = { onOrderClick(order.id) },
                                                onLongPress = { onOrderLongPress(order.id) },
                                                onSwipeAction = { direction ->
                                                    onSwipeAction(order.id, order.reference, direction)
                                                },
                                                availableStatuses = availableStatuses,
                                                swipeConfig = swipeConfig,
                                            )
                                        }
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

                        // Bouton « Charger plus » (pagination)
                        if (hasMore || isLoadingMore) {
                            item {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = Dimensions.spacingS),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Dimensions.iconSizeMedium),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Button(onClick = onLoadMore) {
                                            Text(stringResource(R.string.orders_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Ligne commande avec swipe ────────────────────────────────────────────────

/** Fraction de la largeur de la ligne à partir de laquelle un relâchement déclenche l'action swipe. */
private const val SWIPE_DISMISS_THRESHOLD_FRACTION = 0.25f

/**
 * Spec d'animation de retour/règlement de la ligne après relâchement du doigt — un `spring` plutôt
 * qu'un `tween` linéaire pour retrouver le ressenti « rebond » que `SwipeToDismissBox` donnait
 * gratuitement (cf. KDoc de [SwipeableOrderRow]). Se joue aussi bien quand le swipe est annulé
 * (retour à 0 pur) que quand il est commité (retour à 0 après lecture du seuil, l'action métier
 * étant déclenchée en parallèle, non bloquée par cette animation).
 */
private val swipeSettleAnimSpec: AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Enveloppe une [OrderRow] avec un geste de swipe horizontal pour les commandes en « Paiement
 * accepté ». Implémentation via un **détecteur de geste unique et déterministe**
 * (`pointerInput` + `awaitEachGesture`), PAS deux détecteurs concurrents (l'ancienne version posait
 * `detectHorizontalDragGestures` sur ce `Box` ET laissait `combinedClickable` sur [OrderRow] :
 * sur device réel, un swipe **lent** fonctionnait car le tap de l'enfant se laissait le temps
 * d'observer la consommation du parent, mais un **flick rapide** était capté comme un tap — le
 * détecteur de tap de [OrderRow] voit l'évènement de relâchement (up) avant que le parent n'ait pu
 * clairement établir/consommer suffisamment de deltas de drag, et un tap-detector standard
 * (`detectTapGestures`/`clickable`) traite tout up non explicitement annulé comme un tap, quelle
 * que soit la distance parcourue avant. `SwipeToDismissBox`/`AnchoredDraggableState` (material3
 * 1.3.1) avait le même défaut, en pire : sa reconnaissance interne (seuil + vélocité) ne consomme
 * le geste qu'une fois la transition « dragging » effectivement engagée côté état.
 *
 * Le vrai correctif n'est pas dans la state machine : c'est de ne PLUS avoir deux détecteurs qui se
 * courent après. Un seul `awaitEachGesture` ici tranche tap / appui-long / swipe **avant** que
 * quiconque d'autre ne voie l'évènement de relâchement — [OrderRow] est rendu sans
 * `combinedClickable` dans ce chemin (`enableClick = false`), donc il n'existe plus de second
 * détecteur susceptible de gagner la course. Déroulé :
 * 1. `awaitFirstDown` capture le doigt.
 * 2. Phase 1 (bornée par `viewConfiguration.longPressTimeoutMillis` via le `withTimeoutOrNull`
 *    natif d'`AwaitPointerEventScope`) : à chaque évènement, on accumule le déplacement horizontal
 *    et vertical depuis le down.
 *    - Un `up` avant tout verdict → **tap** (`onClick`), quelle que soit la vitesse : un flick
 *      rapide qui reste sous `touchSlop` est un vrai tap, pas un swipe raté.
 *    - Dès que le déplacement horizontal dépasse `touchSlop` ET domine le vertical → **swipe** :
 *      `change.consume()` (fait céder le scroll du `LazyColumn`) puis bascule en phase 2. C'est
 *      justement ce qui manquait : un flick rapide qui parcourt une vraie distance horizontale
 *      avant l'up est détecté ICI, sur le même évènement de mouvement, AVANT que l'up ne puisse
 *      jamais être examiné comme un tap — il n'y a plus de race, la décision est prise en amont.
 *    - Dès que le déplacement vertical dépasse `touchSlop` ET domine l'horizontal → on **abandonne**
 *      sans consommer, pour laisser le scroll vertical ambiant du `LazyColumn` prendre le relai.
 *    - Le timeout s'écoule sans verdict (doigt immobile) → **appui long** (`onLongPress`, mode
 *      sélection), puis on consomme jusqu'au relâchement.
 * 3. Phase 2 (swipe engagé) : suit le doigt (met à jour [offsetX]) jusqu'au relâchement, puis
 *    calcule si le seuil de 25 % de la largeur est franchi pour déclencher [onSwipeAction] (sinon
 *    anime un retour à 0).
 *
 * La position affichée ([offsetX], un simple [Animatable] `remember`é, PAS `rememberSaveable`)
 * repart toujours de 0f à chaque composition fraîche — y compris après un aller-retour vers l'écran
 * de détail (l'écran Commandes reste dans le back stack, `SwipeableOrderRow` est disposé/recomposé) :
 * aucun état de drag partiel ne peut donc jamais rester « collé ».
 */
private sealed interface PreDragOutcome {
    /** Relâchement (up) observé avant tout verdict de drag → tap simple. */
    data object Tap : PreDragOutcome

    /** Seuil horizontal franchi et dominant → bascule en suivi de swipe (phase 2). */
    data object DragStarted : PreDragOutcome

    /** Seuil vertical franchi et dominant → geste abandonné au profit du scroll du LazyColumn. */
    data object VerticalWin : PreDragOutcome
}

@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
@Composable
private fun SwipeableOrderRow(
    order: Order,
    dateFormatter: DateTimeFormatter,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeAction: (SwipeDirection) -> Unit,
    availableStatuses: List<OrderStatusFilter>,
    swipeConfig: SwipeConfig = SwipeConfig(),
) {
    // Le swipe est actif uniquement si le statut de la commande correspond à la source configurée.
    // Par défaut (aucun ID configuré) : Paiement accepté (id PrestaShop stable), par ID plutôt que
    // par nom FR — robuste quelle que soit la langue d'affichage des statuts.
    val isSwipeSource =
        remember(order.currentStateId, swipeConfig, availableStatuses) {
            if (!swipeConfig.enabled) {
                false
            } else {
                order.currentStateId == (swipeConfig.sourceStatusId ?: SWIPE_DEFAULT_SOURCE_ID)
            }
        }

    if (!isSwipeSource || selectionMode) {
        // Hors contexte swipe : simple ligne
        OrderRow(
            order = order,
            dateFormatter = dateFormatter,
            selectionMode = selectionMode,
            isSelected = isSelected,
            onClick = onClick,
            onLongPress = onLongPress,
            availableStatuses = availableStatuses,
        )
        return
    }

    // Couleur du fond révélé pendant le drag : idéalement la teinte du statut cible réel (celui
    // vers lequel le swipe bascule la commande), résolue via la même logique que
    // OrdersViewModel.onSwipeAction (config swipe → ID configuré, sinon défaut par ID PrestaShop
    // stable). Repli sur une couleur fixe si le statut cible est introuvable ou sans couleur.
    val leftTargetStatus =
        remember(swipeConfig, availableStatuses) {
            resolveSwipeTargetStatus(swipeConfig, availableStatuses, SwipeDirection.LEFT)
        }
    val rightTargetStatus =
        remember(swipeConfig, availableStatuses) {
            resolveSwipeTargetStatus(swipeConfig, availableStatuses, SwipeDirection.RIGHT)
        }
    val leftActionColor =
        remember(leftTargetStatus) { leftTargetStatus?.color?.let(::parseHexColor) }
            ?: MaterialTheme.colorScheme.tertiary
    val rightActionColor =
        remember(rightTargetStatus) { rightTargetStatus?.color?.let(::parseHexColor) }
            ?: Color(0xFF2E7D32)

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .onSizeChanged { size -> rowWidthPx = size.width.toFloat() }
                .pointerInput(order.id) {
                    awaitEachGesture {
                        val touchSlop = viewConfiguration.touchSlop
                        val down = awaitFirstDown(requireUnconsumed = false)

                        var horizontalDrag = 0f
                        var verticalDrag = 0f

                        // Phase 1 : course tap / appui-long / démarrage de drag, bornée par le
                        // délai d'appui long. Cf. KDoc de SwipeableOrderRow pour le détail complet
                        // de l'arbitrage — c'est ICI, sur un déplacement encore en cours, que le
                        // swipe est reconnu, avant que quiconque n'examine jamais l'up comme un tap.
                        val preDragOutcome =
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                                    if (!change.pressed) {
                                        change.consume()
                                        return@withTimeoutOrNull PreDragOutcome.Tap
                                    }

                                    val delta = change.positionChange()
                                    horizontalDrag += delta.x
                                    verticalDrag += delta.y
                                    val absHorizontal = abs(horizontalDrag)
                                    val absVertical = abs(verticalDrag)

                                    when {
                                        absHorizontal > touchSlop && absHorizontal > absVertical -> {
                                            change.consume()
                                            return@withTimeoutOrNull PreDragOutcome.DragStarted
                                        }
                                        absVertical > touchSlop && absVertical >= absHorizontal -> {
                                            return@withTimeoutOrNull PreDragOutcome.VerticalWin
                                        }
                                        else -> {
                                            // Sous le seuil dans les deux sens : encore indéterminé,
                                            // on continue d'observer (candidat tap/appui-long/swipe).
                                        }
                                    }
                                }
                            }

                        when (preDragOutcome) {
                            PreDragOutcome.Tap -> onClick()
                            // Scroll vertical : on n'a rien consommé, le LazyColumn prend le relai.
                            PreDragOutcome.VerticalWin -> Unit
                            PreDragOutcome.DragStarted -> {
                                scope.launch {
                                    offsetX.snapTo(horizontalDrag.coerceIn(-rowWidthPx, rowWidthPx))
                                }
                                var releasedWhileDragging = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        releasedWhileDragging = true
                                        break
                                    }
                                    val delta = change.positionChange()
                                    val newOffset = (offsetX.value + delta.x).coerceIn(-rowWidthPx, rowWidthPx)
                                    scope.launch { offsetX.snapTo(newOffset) }
                                }

                                val threshold = rowWidthPx * SWIPE_DISMISS_THRESHOLD_FRACTION
                                val committedDirection =
                                    when {
                                        !releasedWhileDragging || rowWidthPx <= 0f -> null
                                        offsetX.value <= -threshold -> SwipeDirection.LEFT
                                        offsetX.value >= threshold -> SwipeDirection.RIGHT
                                        else -> null
                                    }
                                scope.launch { offsetX.animateTo(0f, swipeSettleAnimSpec) }
                                // Déclenché APRÈS avoir lancé l'animation de retour (non bloquant) : la
                                // fenêtre d'annulation métier (SwipeUndoBar) démarre immédiatement, sans
                                // attendre la fin de l'anim visuelle de règlement de la ligne.
                                committedDirection?.let(onSwipeAction)
                            }
                            // Timeout écoulé sans verdict (doigt resté quasi immobile) → appui long.
                            null -> {
                                onLongPress()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            }
                        }
                    }
                },
    ) {
        SwipeActionBackground(
            offsetXPx = offsetX.value,
            leftActionColor = leftActionColor,
            rightActionColor = rightActionColor,
        )
        Box(
            modifier =
                Modifier.offset {
                    IntOffset(offsetX.value.roundToInt(), 0)
                },
        ) {
            OrderRow(
                order = order,
                dateFormatter = dateFormatter,
                selectionMode = selectionMode,
                isSelected = isSelected,
                onClick = onClick,
                onLongPress = onLongPress,
                availableStatuses = availableStatuses,
                enableClick = false,
            )
        }
    }
}

/**
 * Fond coloré + icône/label révélé derrière [OrderRow] pendant le drag, selon le sens du swipe.
 * Extension de [BoxScope] : `matchParentSize()` a besoin de ce receiver pour se caler sur la taille
 * du `Box` ancêtre (déterminée par [OrderRow], l'autre enfant de ce même `Box`).
 */
@Composable
private fun BoxScope.SwipeActionBackground(
    offsetXPx: Float,
    leftActionColor: Color,
    rightActionColor: Color,
) {
    val isLeftSwipe = offsetXPx < 0f
    val isRightSwipe = offsetXPx > 0f
    val bgColor =
        when {
            isLeftSwipe -> leftActionColor
            isRightSwipe -> rightActionColor
            else -> Color.Transparent
        }
    val swipeIcon = if (isLeftSwipe) Icons.Outlined.ArrowUpward else Icons.Outlined.Done
    val swipeLabel =
        when {
            isLeftSwipe -> stringResource(R.string.orders_swipe_left_label)
            isRightSwipe -> stringResource(R.string.orders_swipe_right_label)
            else -> ""
        }
    val swipeAlign = if (isRightSwipe) Alignment.CenterStart else Alignment.CenterEnd
    val onBgColor = contrastTextColor(bgColor)
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .background(bgColor)
                .padding(horizontal = Dimensions.spacingL),
        contentAlignment = swipeAlign,
    ) {
        if (isLeftSwipe || isRightSwipe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
            ) {
                Icon(
                    imageVector = swipeIcon,
                    contentDescription = null,
                    tint = onBgColor,
                    modifier = Modifier.size(Dimensions.iconSizeSmall),
                )
                Text(
                    text = swipeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = onBgColor,
                )
            }
        }
    }
}

// ─── Barre d'action sélection ────────────────────────────────────────────────

@Suppress("LongParameterList")
@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    isPrintingInProgress: Boolean,
    isBulkUpdating: Boolean,
    onCancel: () -> Unit,
    onPrint: () -> Unit,
    onChangeStatus: () -> Unit,
    hasStatuses: Boolean,
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

            when {
                isBulkUpdating -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.iconSizeMedium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp,
                    )
                }
                isPrintingInProgress -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.iconSizeMedium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasStatuses) {
                            IconButton(
                                onClick = onChangeStatus,
                                enabled = selectedCount > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.orders_selection_change_status),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        IconButton(
                            onClick = onPrint,
                            enabled = selectedCount > 0,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Print,
                                contentDescription = stringResource(R.string.orders_selection_print, selectedCount),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Ligne de commande ────────────────────────────────────────────────────────

/**
 * Ligne de commande — design Stitch : avatar initiales, nom + référence,
 * badge statut coloré, montant aligné à droite.
 *
 * [enableClick] : `false` dans le chemin swipable ([SwipeableOrderRow]), où le tap/appui-long est
 * déjà entièrement arbitré par le `pointerInput` unique de l'ancêtre — poser aussi un
 * `combinedClickable` ici recréerait exactement le second détecteur concurrent à l'origine du bug
 * du flick capté comme un tap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList", "LongMethod")
@Composable
private fun OrderRow(
    order: Order,
    dateFormatter: DateTimeFormatter,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    availableStatuses: List<OrderStatusFilter> = emptyList(),
    enableClick: Boolean = true,
) {
    val amountText =
        remember(order.totalPaid, order.currency) {
            formatCurrency(order.totalPaid, order.currency)
        }
    val updatedAt =
        remember(order.createdAtIso) {
            formatTimestamp(order.createdAtIso, dateFormatter) ?: order.createdAtIso
        }
    val status = order.status.ifBlank { stringResource(id = R.string.orders_status_unknown) }

    // Couleur du badge : depuis l'ordre (connecteur v1.9+) ou fallback dans availableStatuses
    val resolvedStatusColor =
        remember(order.statusColor, order.currentStateId, availableStatuses) {
            order.statusColor?.takeIf { it.isNotBlank() }
                ?: availableStatuses.firstOrNull { it.id == order.currentStateId }?.color
        }

    val isSelectable = !selectionMode || order.hasInvoice
    val rowAlpha = if (selectionMode && !order.hasInvoice) 0.4f else 1f

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(rowAlpha)
                .then(
                    if (isSelectable && enableClick) {
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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
        ) {
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
                OrderStatusBadge(status = status, statusColor = resolvedStatusColor)
            }
        }
    }
}

@Composable
private fun rememberDateFormatter(): DateTimeFormatter =
    remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
    }

// ─── Chip de filtre période (depuis dashboard) ────────────────────────────────

/**
 * Chip dismissible affiché quand un filtre de période dashboard est actif.
 * Libellé = libellé de la période (ex. "7 days") ; croix = efface le filtre.
 */
@Composable
private fun PeriodFilterChip(
    period: DashboardPeriod,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(stringResource(R.string.orders_period_chip, stringResource(period.labelRes()))) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.orders_period_chip_clear),
                modifier = Modifier.size(Dimensions.iconSizeSmall),
            )
        },
        modifier = modifier,
    )
}

// ─── Barre de filtres par statut + tri ────────────────────────────────────────

/**
 * Ligne horizontale scrollable de chips de raccourcis (sélection EXCLUSIVE) + bouton de tri + ⚙.
 *
 * Pas de chip « Toutes » (retiré, doublon avec le re-tap) : **rien de sélectionné = toutes les
 * commandes affichées** est l'état par défaut de [selectedStatusIds] (ensemble vide).
 * - Chaque chip de statut : sélection exclusive → appelle [onStatusToggle] avec l'ID, qui ISOLE ce
 *   statut (n'affiche QUE lui), y compris s'il faisait déjà partie du filtre par défaut multi-statuts
 *   (cf. KDoc de [OrdersViewModel.onStatusFilterSelected]). Re-tap sur l'unique statut déjà
 *   sélectionné → désélectionne (retour à « toutes affichées », seul moyen de tout réafficher
 *   depuis cette barre).
 *   Le vrai multi-statuts (plusieurs à la fois) se fait via le menu ⚙ « Filtrer par statut »
 *   (checkboxes dédiées), pas ces chips.
 * - Bouton tri (Sort icon) : ouvre un menu déroulant avec les options de tri
 * - Bouton ⚙ : ouvre le menu « Filtrer par statut » + « Raccourcis ». Un point apparaît dessus
 *   quand [hasHiddenActiveFilter] est vrai (filtre actif sur un statut absent des chips).
 */
@Composable
private fun StatusFilterBar(
    statuses: List<OrderStatusFilter>,
    selectedStatusIds: Set<Int>,
    onStatusToggle: (Int) -> Unit,
    onConfigureClick: () -> Unit,
    selectedSort: OrderSort,
    onSortChanged: (OrderSort) -> Unit,
    modifier: Modifier = Modifier,
    hasHiddenActiveFilter: Boolean = false,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Bouton de tri
        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(
                    imageVector =
                        when (selectedSort) {
                            OrderSort.DATE_ASC, OrderSort.AMOUNT_ASC -> Icons.Outlined.ArrowUpward
                            OrderSort.DATE_DESC, OrderSort.AMOUNT_DESC -> Icons.Outlined.ArrowDownward
                            else -> Icons.AutoMirrored.Outlined.Sort
                        },
                    contentDescription = stringResource(R.string.orders_sort_label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                SortOption(OrderSort.DATE_DESC, selectedSort, stringResource(R.string.orders_sort_date_desc)) {
                    onSortChanged(it)
                    showSortMenu = false
                }
                SortOption(OrderSort.DATE_ASC, selectedSort, stringResource(R.string.orders_sort_date_asc)) {
                    onSortChanged(it)
                    showSortMenu = false
                }
                SortOption(OrderSort.AMOUNT_DESC, selectedSort, stringResource(R.string.orders_sort_amount_desc)) {
                    onSortChanged(it)
                    showSortMenu = false
                }
                SortOption(OrderSort.AMOUNT_ASC, selectedSort, stringResource(R.string.orders_sort_amount_asc)) {
                    onSortChanged(it)
                    showSortMenu = false
                }
                SortOption(OrderSort.STATUS, selectedSort, stringResource(R.string.orders_sort_status)) {
                    onSortChanged(it)
                    showSortMenu = false
                }
                SortOption(OrderSort.REFERENCE, selectedSort, stringResource(R.string.orders_sort_reference)) {
                    onSortChanged(it)
                    showSortMenu = false
                }
            }
        }

        // Chips de filtres multi-sélection
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
        ) {
            statuses.forEach { status ->
                val chipDescription = stringResource(R.string.orders_filter_chip_description, status.name)
                val shortLabelResId = remember(status.id) { statusShortLabelResId(status.id) }
                val shortLabelFallback = remember(status.name) { statusShortLabelFallback(status.name) }
                val shortLabel = shortLabelResId?.let { stringResource(it) } ?: shortLabelFallback
                val dotColor = remember(status.color) { parseHexColor(status.color) }
                FilterChip(
                    modifier = Modifier.semantics { contentDescription = chipDescription },
                    selected = status.id in selectedStatusIds,
                    onClick = { onStatusToggle(status.id) },
                    leadingIcon = {
                        if (dotColor != null) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .background(dotColor, CircleShape),
                            )
                        }
                    },
                    label = { Text(shortLabel) },
                )
            }
        }

        BadgedBox(
            badge = {
                if (hasHiddenActiveFilter) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                }
            },
        ) {
            IconButton(onClick = onConfigureClick) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.orders_filter_configure),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SortOption(
    sort: OrderSort,
    currentSort: OrderSort,
    label: String,
    onSelect: (OrderSort) -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onSelect(sort) },
        leadingIcon =
            if (sort == currentSort) {
                { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) }
            } else {
                null
            },
    )
}

// ─── Bottom sheet « Filtrer par statut » + « Raccourcis » ─────────────────────

/**
 * Bottom sheet du menu statuts, en 2 volets pilotés indépendamment :
 * - **Filtre** ([localFilterSelection]) : multi-sélection SANS limite, sur 100 % des
 *   [availableStatuses]. Piloté par la case à cocher de chaque ligne. Un bouton « Tout effacer »
 *   réinitialise ce volet uniquement.
 * - **Raccourcis** ([localShortcutSelection]) : jusqu'à [MAX_VISIBLE_STATUS_CHIPS] statuts
 *   épinglés dans la barre de chips. Piloté par l'icône épingle de chaque ligne.
 *
 * Les deux sélections locales ne sont appliquées qu'au clic sur « Appliquer » ([onConfirm]),
 * qui reçoit (filterIds, shortcutIds).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPreferencesSheet(
    sheetState: androidx.compose.material3.SheetState,
    availableStatuses: List<OrderStatusFilter>,
    selectedStatusIds: Set<Int>,
    visibleStatusIds: Set<Int>?,
    onDismiss: () -> Unit,
    onConfirm: (filterIds: Set<Int>, shortcutIds: Set<Int>) -> Unit,
) {
    var localFilterSelection by remember(availableStatuses, selectedStatusIds) {
        mutableStateOf(selectedStatusIds)
    }
    // Quand aucune préférence de raccourcis n'est définie, pré-sélectionne le défaut curaté (≤ 3)
    val initialShortcutSelection =
        visibleStatusIds ?: resolveDefaultVisibleChips(availableStatuses).map { it.id }.toSet()
    var localShortcutSelection by remember(availableStatuses, visibleStatusIds) {
        mutableStateOf(initialShortcutSelection)
    }
    val shortcutAtLimit = localShortcutSelection.size >= MAX_VISIBLE_STATUS_CHIPS

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.screenEdgeMargin)
                    .padding(bottom = Dimensions.spacingL),
        ) {
            Text(
                text = stringResource(R.string.orders_filter_prefs_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = Dimensions.spacingS),
            )

            // En-têtes de colonnes : filtre (illimité) à gauche, raccourcis (max N) à droite
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.orders_filter_prefs_section_filter),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(
                    onClick = { localFilterSelection = emptySet() },
                    enabled = localFilterSelection.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.orders_filter_prefs_clear_all))
                }
            }
            Text(
                text = stringResource(R.string.orders_filter_prefs_section_filter_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimensions.spacingS),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Dimensions.spacingXs),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(R.string.orders_filter_prefs_section_shortcuts),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (shortcutAtLimit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXs),
            ) {
                items(availableStatuses, key = { it.id }) { status ->
                    val isFilterChecked = status.id in localFilterSelection
                    val isShortcut = status.id in localShortcutSelection
                    val isShortcutEnabled = isShortcut || !shortcutAtLimit
                    val dotColor = remember(status.color) { parseHexColor(status.color) }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    localFilterSelection =
                                        if (isFilterChecked) {
                                            localFilterSelection - status.id
                                        } else {
                                            localFilterSelection + status.id
                                        }
                                }
                                .padding(vertical = Dimensions.spacingXs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingS),
                    ) {
                        Checkbox(
                            checked = isFilterChecked,
                            onCheckedChange = { checked ->
                                localFilterSelection =
                                    if (checked) localFilterSelection + status.id else localFilterSelection - status.id
                            },
                        )
                        if (dotColor != null) {
                            Box(
                                modifier = Modifier.size(8.dp).background(dotColor, CircleShape),
                            )
                        }
                        Text(
                            text = status.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        val pinDescription =
                            if (isShortcut) {
                                stringResource(R.string.orders_filter_prefs_pin_description_remove, status.name)
                            } else {
                                stringResource(R.string.orders_filter_prefs_pin_description_add, status.name)
                            }
                        IconButton(
                            onClick = {
                                if (isShortcut) {
                                    localShortcutSelection = localShortcutSelection - status.id
                                } else if (!shortcutAtLimit) {
                                    localShortcutSelection = localShortcutSelection + status.id
                                }
                            },
                            enabled = isShortcutEnabled,
                        ) {
                            Icon(
                                imageVector = if (isShortcut) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = pinDescription,
                                tint =
                                    if (isShortcut) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isShortcutEnabled) 1f else 0.38f)
                                    },
                            )
                        }
                    }
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Dimensions.spacingS),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.orders_filter_prefs_cancel))
                }
                TextButton(onClick = { onConfirm(localFilterSelection, localShortcutSelection) }) {
                    Text(stringResource(R.string.orders_filter_prefs_confirm))
                }
            }
        }
    }
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
                                status = "Paiement accepté",
                                totalPaid = 45.0,
                                currency = "EUR",
                                customerName = "Marie Dupont",
                                createdAtIso = "2026-06-19T14:20:00Z",
                                updatedAtIso = "2026-06-19T14:20:00Z",
                                hasInvoice = true,
                                statusColor = "#28A745",
                            ),
                            Order(
                                id = 2L,
                                reference = "#ORD-8491",
                                status = "Expédiée",
                                totalPaid = 28.50,
                                currency = "EUR",
                                customerName = "Julien Martin",
                                createdAtIso = "2026-06-18T09:15:00Z",
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
                                createdAtIso = "2026-11-02T16:45:00Z",
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
                                createdAtIso = "2026-06-19T14:20:00Z",
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
                                createdAtIso = "2026-06-18T09:15:00Z",
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
