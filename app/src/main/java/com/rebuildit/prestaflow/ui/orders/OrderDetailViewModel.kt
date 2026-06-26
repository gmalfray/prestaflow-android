package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ordersRepository: OrdersRepository,
    ) : ViewModel() {
        private val orderId: Long = checkNotNull(savedStateHandle["orderId"])

        val uiState: StateFlow<OrderDetailUiState> =
            ordersRepository.getOrder(orderId)
                .map { order ->
                    if (order != null) {
                        OrderDetailUiState.Success(order)
                    } else {
                        OrderDetailUiState.Loading
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = OrderDetailUiState.Loading,
                )

        private val _actionState = MutableStateFlow(OrderActionState())
        val actionState: StateFlow<OrderActionState> = _actionState.asStateFlow()

        private val _availableStatuses = MutableStateFlow<List<OrderStatusFilter>>(emptyList())
        val availableStatuses: StateFlow<List<OrderStatusFilter>> = _availableStatuses.asStateFlow()

        init {
            viewModelScope.launch {
                runCatching {
                    ordersRepository.refreshOrder(orderId)
                }.onFailure { error ->
                    // Les données en cache (issues de la liste) restent affichées, mais
                    // sans articles/livraison : on remonte l'échec au lieu de l'avaler
                    // pour diagnostiquer (ex. endpoint détail du connecteur indisponible).
                    Timber.w(error, "Échec du chargement du détail commande #%d", orderId)
                    _actionState.update {
                        it.copy(error = "Détail indisponible : affichage des données en cache")
                    }
                }
            }
            loadStatuses()
        }

        /** Charge les statuts disponibles depuis l'API (silencieux en cas d'erreur). */
        private fun loadStatuses() {
            viewModelScope.launch {
                runCatching { ordersRepository.getOrderStatuses() }
                    .onSuccess { statuses -> _availableStatuses.value = statuses }
                    .onFailure { error ->
                        Timber.w(error, "Impossible de charger les statuts pour le détail commande")
                    }
            }
        }

        fun updateStatus(status: String) {
            val trimmed = status.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.updateOrderStatus(orderId, trimmed)
                }.onSuccess {
                    _actionState.update { state -> state.copy(inProgress = false, message = "Status updated") }
                }.onFailure { error ->
                    _actionState.update { state -> state.copy(inProgress = false, error = error.message ?: "Update failed") }
                }
            }
        }

        fun updateTracking(trackingNumber: String) {
            val trimmed = trackingNumber.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.updateOrderShipping(orderId, trimmed)
                }.onSuccess {
                    _actionState.update { state -> state.copy(inProgress = false, message = "Tracking updated") }
                }.onFailure { error ->
                    _actionState.update { state -> state.copy(inProgress = false, error = error.message ?: "Update failed") }
                }
            }
        }

        fun consumeActionFeedback() {
            _actionState.update { it.copy(message = null, error = null) }
        }

        /**
         * Télécharge la facture PDF de la commande courante.
         * Le résultat (octets PDF ou null si absent) est émis via [actionState].
         */
        fun fetchInvoicePdf(onReady: (ByteArray) -> Unit) {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.downloadInvoicePdf(orderId)
                }.onSuccess { bytes ->
                    _actionState.update { it.copy(inProgress = false) }
                    if (bytes != null) {
                        onReady(bytes)
                    } else {
                        _actionState.update { it.copy(error = "Cette commande n'a pas de facture disponible.") }
                    }
                }.onFailure { error ->
                    _actionState.update { it.copy(inProgress = false, error = error.message ?: "Échec du téléchargement de la facture") }
                }
            }
        }

        /**
         * Télécharge le bordereau de transport PDF de la commande courante.
         * Le résultat (octets PDF ou null si absent) est émis via [actionState].
         */
        fun fetchShippingLabelPdf(onReady: (ByteArray) -> Unit) {
            viewModelScope.launch {
                _actionState.update { it.copy(inProgress = true, error = null) }
                runCatching {
                    ordersRepository.downloadShippingLabel(orderId)
                }.onSuccess { bytes ->
                    _actionState.update { it.copy(inProgress = false) }
                    if (bytes != null) {
                        onReady(bytes)
                    } else {
                        _actionState.update { it.copy(error = "Aucun bordereau disponible pour cette commande.") }
                    }
                }.onFailure { error ->
                    _actionState.update { it.copy(inProgress = false, error = error.message ?: "Échec du téléchargement du bordereau") }
                }
            }
        }
    }

data class OrderActionState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState

    data class Success(val order: Order) : OrderDetailUiState

    data object Error : OrderDetailUiState
}
