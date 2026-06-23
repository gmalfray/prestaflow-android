package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
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
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel
    @Inject
    constructor(
        private val ordersRepository: OrdersRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OrdersUiState())
        val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

        init {
            observeOrders()
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
                runCatching { ordersRepository.refresh(forceRemote, statusId) }
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
    /** Message d'erreur d'impression à afficher puis consommer. */
    val printError: String? = null,
    /** Statuts disponibles pour le filtre, chargés depuis l'API. */
    val availableStatuses: List<OrderStatusFilter> = emptyList(),
    /** ID du statut sélectionné comme filtre, null = tous les statuts. */
    val selectedStatusId: Int? = null,
) {
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
