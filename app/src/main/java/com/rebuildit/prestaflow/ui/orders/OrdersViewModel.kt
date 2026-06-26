package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ordersRepository: OrdersRepository,
        private val ordersPreferencesRepository: OrdersPreferencesRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OrdersUiState())
        val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

        /**
         * Plage de dates dérivée de la période dashboard transmise via nav arg "period".
         * Null si l'écran est ouvert sans filtre de période (accès direct depuis la barre de navigation).
         */
        private val periodDateRange: Pair<String, String>? =
            savedStateHandle.get<String?>("period")
                ?.let { periodValue -> DashboardPeriod.entries.find { it.queryValue == periodValue } }
                ?.toDateRange()

        init {
            observeOrders()
            observeVisibleStatusIds()
            refresh(forceRemote = true, notifyOnError = false)
            loadStatuses()
            observeActiveShopSwitch()
        }

        fun onRefresh() {
            refresh(forceRemote = true, notifyOnError = true)
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        /** Charge les statuts disponibles depuis l'API (silencieux en cas d'erreur). */
        private fun loadStatuses() {
            viewModelScope.launch {
                runCatching { ordersRepository.getOrderStatuses() }
                    .onSuccess { statuses ->
                        _uiState.update { it.copy(availableStatuses = statuses) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Impossible de charger les statuts de commande")
                    }
            }
        }

        /** Sélectionne (ou désélectionne) le filtre par statut puis recharge la liste. */
        fun onStatusFilterSelected(statusId: Int?) {
            _uiState.update { it.copy(selectedStatusId = statusId) }
            refresh(forceRemote = true, notifyOnError = true)
        }

        // ─── Préférence de statuts visibles ─────────────────────────────────────

        /** Observe la préférence DataStore et met à jour l'état. */
        private fun observeVisibleStatusIds() {
            viewModelScope.launch {
                ordersPreferencesRepository.visibleStatusIds.collect { ids ->
                    _uiState.update { current ->
                        val newState = current.copy(visibleStatusIds = ids)
                        // Si le statut sélectionné n'est plus visible, retomber sur « Toutes »
                        val newSelectedId =
                            if (ids != null && current.selectedStatusId != null &&
                                current.selectedStatusId !in ids
                            ) {
                                null
                            } else {
                                current.selectedStatusId
                            }
                        newState.copy(selectedStatusId = newSelectedId)
                    }
                }
            }
        }

        /**
         * Persiste les IDs de statuts à afficher dans la barre de filtres.
         * Si [ids] est null ou vide, réinitialise la préférence (tous les statuts affichés).
         */
        fun onVisibleStatusIdsChanged(ids: Set<Int>) {
            viewModelScope.launch {
                if (ids.isEmpty()) {
                    ordersPreferencesRepository.clearVisibleStatusIds()
                } else {
                    ordersPreferencesRepository.setVisibleStatusIds(ids)
                }
            }
        }

        // ─── Sélection multiple ──────────────────────────────────────────────────

        /** Active le mode sélection et sélectionne la commande [orderId] (appui long). */
        fun onOrderLongPress(orderId: Long) {
            _uiState.update { current ->
                val order = current.orders.find { it.id == orderId }
                // Les commandes sans facture ne sont pas sélectionnables
                if (order == null || !order.hasInvoice) return@update current
                current.copy(
                    selectionMode = true,
                    selectedOrderIds = current.selectedOrderIds + orderId,
                )
            }
        }

        /** Bascule la sélection d'une commande (en mode sélection actif). */
        fun onOrderSelectionToggle(orderId: Long) {
            _uiState.update { current ->
                if (!current.selectionMode) return@update current
                val order = current.orders.find { it.id == orderId }
                if (order == null || !order.hasInvoice) return@update current
                val newSelection =
                    if (orderId in current.selectedOrderIds) {
                        current.selectedOrderIds - orderId
                    } else {
                        current.selectedOrderIds + orderId
                    }
                current.copy(
                    selectionMode = newSelection.isNotEmpty(),
                    selectedOrderIds = newSelection,
                )
            }
        }

        /** Quitte le mode sélection sans déclencher d'impression. */
        fun cancelSelection() {
            _uiState.update { it.copy(selectionMode = false, selectedOrderIds = emptySet()) }
        }

        /**
         * Change le statut de toutes les commandes sélectionnées vers [statusId].
         *
         * - Itère sur chaque commande sélectionnée et appelle [updateOrderStatus] individuellement
         *   pour ne pas bloquer l'ensemble en cas d'échec partiel.
         * - Expose [OrdersUiState.isBulkUpdating] pendant l'opération.
         * - Émet un snackbar résumant les succès / échecs.
         * - Rafraîchit la liste et quitte le mode sélection à la fin (succès ou non).
         * - Robuste hors-ligne : une exception par commande est catchée individuellement.
         */
        fun bulkUpdateStatus(statusId: String) {
            val selectedIds = _uiState.value.selectedOrderIds.toList()
            if (selectedIds.isEmpty()) return
            viewModelScope.launch {
                _uiState.update { it.copy(isBulkUpdating = true) }
                var successCount = 0
                var failureCount = 0
                selectedIds.forEach { orderId ->
                    runCatching {
                        ordersRepository.updateOrderStatus(orderId, statusId)
                    }.onSuccess {
                        successCount++
                    }.onFailure { error ->
                        failureCount++
                        Timber.w(error, "Échec mise à jour statut commande #%d", orderId)
                    }
                }
                _uiState.update { current ->
                    val message =
                        if (failureCount == 0) {
                            "$successCount commande(s) mise(s) à jour"
                        } else {
                            "$successCount mise(s) à jour, $failureCount échec(s)"
                        }
                    current.copy(
                        isBulkUpdating = false,
                        selectionMode = false,
                        selectedOrderIds = emptySet(),
                        bulkSnackbar = message,
                    )
                }
                // Rafraîchir la liste pour refléter les nouveaux statuts
                refresh(forceRemote = true, notifyOnError = false)
            }
        }

        /** Consomme le message snackbar de mise à jour en lot. */
        fun consumeBulkSnackbar() {
            _uiState.update { it.copy(bulkSnackbar = null) }
        }

        /**
         * Télécharge les PDFs des commandes sélectionnées et invoque [onReady] avec les octets.
         * Remet à zéro la sélection après succès.
         */
        fun printSelectedInvoices(onReady: (List<ByteArray>) -> Unit) {
            val selectedIds = _uiState.value.selectedOrderIds.toList()
            if (selectedIds.isEmpty()) return
            viewModelScope.launch {
                _uiState.update { it.copy(isPrintingInProgress = true) }
                runCatching {
                    selectedIds.mapNotNull { id -> ordersRepository.downloadInvoicePdf(id) }
                }.onSuccess { pdfList ->
                    _uiState.update { it.copy(isPrintingInProgress = false, selectionMode = false, selectedOrderIds = emptySet()) }
                    if (pdfList.isNotEmpty()) onReady(pdfList)
                }.onFailure { error ->
                    Timber.w(error, "Échec du téléchargement des factures sélectionnées")
                    _uiState.update { it.copy(isPrintingInProgress = false, printError = error.message ?: "Erreur d'impression") }
                }
            }
        }

        /** Consomme le message d'erreur d'impression. */
        fun consumePrintError() {
            _uiState.update { it.copy(printError = null) }
        }

        // ─── Rafraîchissement ────────────────────────────────────────────────────

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(
                                orders = emptyList(),
                                isLoading = true,
                                error = null,
                                selectionMode = false,
                                selectedOrderIds = emptySet(),
                                selectedStatusId = null,
                                availableStatuses = emptyList(),
                            )
                        }
                        loadStatuses()
                        refresh(forceRemote = true, notifyOnError = true)
                    }
            }
        }

        private fun observeOrders() {
            viewModelScope.launch {
                ordersRepository.observeOrders().collect { orders ->
                    _uiState.update { current ->
                        current.copy(
                            orders = orders,
                            isLoading = false,
                            isRefreshing = false,
                            error = if (orders.isNotEmpty()) null else current.error,
                        )
                    }
                }
            }
        }

        fun refresh(
            forceRemote: Boolean,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.orders.isEmpty(),
                        error = if (notifyOnError) null else current.error,
                    )
                }

                val statusId = _uiState.value.selectedStatusId
                val (dateFrom, dateTo) = periodDateRange ?: Pair(null, null)
                runCatching { ordersRepository.refresh(forceRemote, statusId, dateFrom, dateTo) }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh orders")
                        _uiState.update { current ->
                            val mapped = networkErrorMapper.map(error)
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.orders.isEmpty(),
                                error = if (notifyOnError) mapped else current.error,
                            )
                        }
                    }
                    .onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.orders.isEmpty(),
                                error = null,
                            )
                        }
                    }
            }
        }
    }

/**
 * Convertit une [DashboardPeriod] en plage de dates (dateFrom, dateTo) au format "Y-m-d",
 * en reprenant exactement la même logique que [DashboardService::resolvePeriodRange] côté PHP
 * (baseé sur la date locale du device ; la boutique utilise son propre fuseau côté serveur).
 */
private fun DashboardPeriod.toDateRange(): Pair<String, String> {
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE
    val today = LocalDate.now()
    val (from, to) =
        when (this) {
            DashboardPeriod.TODAY -> Pair(today, today)
            DashboardPeriod.WEEK -> Pair(today.minusDays(6), today)
            DashboardPeriod.MONTH -> Pair(today.minusDays(29), today)
            DashboardPeriod.QUARTER -> Pair(today.minusMonths(3), today)
            DashboardPeriod.YEAR -> Pair(today.withDayOfYear(1), today)
        }
    return Pair(from.format(fmt), to.format(fmt))
}

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    /** Mode sélection multiple actif (déclenché par appui long). */
    val selectionMode: Boolean = false,
    /** IDs des commandes sélectionnées (toutes avec has_invoice=true). */
    val selectedOrderIds: Set<Long> = emptySet(),
    /** Vrai pendant le téléchargement des PDFs pour impression. */
    val isPrintingInProgress: Boolean = false,
    /** Vrai pendant la mise à jour de statut en lot. */
    val isBulkUpdating: Boolean = false,
    /** Message d'erreur d'impression à afficher puis consommer. */
    val printError: String? = null,
    /** Message snackbar résumant le résultat de la mise à jour en lot. */
    val bulkSnackbar: String? = null,
    /** Statuts disponibles pour le filtre, chargés depuis l'API. */
    val availableStatuses: List<OrderStatusFilter> = emptyList(),
    /** ID du statut sélectionné comme filtre, null = tous les statuts. */
    val selectedStatusId: Int? = null,
    /**
     * IDs des statuts à afficher dans la barre de filtres (préférence persistée).
     * Null = aucune préférence → tous les [availableStatuses] sont affichés.
     */
    val visibleStatusIds: Set<Int>? = null,
) {
    /**
     * Statuts effectivement affichés dans la barre de filtres.
     * Si [visibleStatusIds] est null, tous les [availableStatuses] sont retournés (comportement par défaut).
     */
    val filteredStatuses: List<OrderStatusFilter>
        get() =
            visibleStatusIds?.let { ids ->
                availableStatuses.filter { it.id in ids }
            } ?: availableStatuses

    /** Liste filtrée par [query] sur le nom du client et la référence (insensible à la casse). */
    val visibleOrders: List<Order>
        get() =
            if (query.isBlank()) {
                orders
            } else {
                orders.filter {
                    it.customerName.contains(query, ignoreCase = true) ||
                        it.reference.contains(query, ignoreCase = true)
                }
            }
}
