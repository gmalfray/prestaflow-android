package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel du panneau détail commande dans le layout deux colonnes (tablette).
 *
 * Contrairement à [OrderDetailViewModel] qui lit l'orderId depuis le [SavedStateHandle]
 * (route de navigation), ce ViewModel reçoit l'orderId via [selectOrder] et est partagé
 * avec la durée de vie du composable parent du two-pane layout.
 */
@HiltViewModel
class OrderDetailPaneViewModel
    @Inject
    constructor(
        private val ordersRepository: OrdersRepository,
    ) : ViewModel() {
        private val selectedOrderIdFlow = MutableStateFlow<Long?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<OrderDetailUiState> =
            selectedOrderIdFlow
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(OrderDetailUiState.Loading)
                    } else {
                        ordersRepository.getOrder(id).map { order ->
                            if (order != null) {
                                OrderDetailUiState.Success(order)
                            } else {
                                OrderDetailUiState.Error
                            }
                        }
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = OrderDetailUiState.Loading,
                )

        private val _actionState = MutableStateFlow(OrderActionState())
        val actionState: StateFlow<OrderActionState> = _actionState

        fun selectOrder(orderId: Long) {
            if (selectedOrderIdFlow.value == orderId) return
            selectedOrderIdFlow.value = orderId
            _actionState.value = OrderActionState()
            viewModelScope.launch {
                runCatching { ordersRepository.refreshOrder(orderId) }
            }
        }

        fun clearSelection() {
            selectedOrderIdFlow.value = null
        }

        fun updateStatus(status: String) {
            val id = selectedOrderIdFlow.value ?: return
            val trimmed = status.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.value = _actionState.value.copy(inProgress = true, error = null)
                runCatching {
                    ordersRepository.updateOrderStatus(id, trimmed)
                }.onSuccess {
                    _actionState.value = _actionState.value.copy(inProgress = false, message = "Status updated")
                }.onFailure { error ->
                    _actionState.value = _actionState.value.copy(inProgress = false, error = error.message ?: "Update failed")
                }
            }
        }

        fun updateTracking(trackingNumber: String) {
            val id = selectedOrderIdFlow.value ?: return
            val trimmed = trackingNumber.trim()
            if (trimmed.isEmpty()) return
            viewModelScope.launch {
                _actionState.value = _actionState.value.copy(inProgress = true, error = null)
                runCatching {
                    ordersRepository.updateOrderShipping(id, trimmed)
                }.onSuccess {
                    _actionState.value = _actionState.value.copy(inProgress = false, message = "Tracking updated")
                }.onFailure { error ->
                    _actionState.value = _actionState.value.copy(inProgress = false, error = error.message ?: "Update failed")
                }
            }
        }

        fun consumeActionFeedback() {
            _actionState.value = _actionState.value.copy(message = null, error = null)
        }
    }
