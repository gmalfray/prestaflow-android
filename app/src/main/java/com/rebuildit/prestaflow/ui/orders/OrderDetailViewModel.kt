package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ordersRepository: OrdersRepository
) : ViewModel() {

    private val orderId: Long = checkNotNull(savedStateHandle["orderId"])

    val uiState: StateFlow<OrderDetailUiState> = ordersRepository.getOrder(orderId)
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
            initialValue = OrderDetailUiState.Loading
        )

    private val _actionState = MutableStateFlow(OrderActionState())
    val actionState: StateFlow<OrderActionState> = _actionState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                ordersRepository.refreshOrder(orderId)
            }.onFailure {
                // Local cached data is still rendered if available.
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
                _actionState.update { it.copy(inProgress = false, message = "Status updated") }
            }.onFailure { error ->
                _actionState.update { it.copy(inProgress = false, error = error.message ?: "Update failed") }
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
                _actionState.update { it.copy(inProgress = false, message = "Tracking updated") }
            }.onFailure { error ->
                _actionState.update { it.copy(inProgress = false, error = error.message ?: "Update failed") }
            }
        }
    }

    fun consumeActionFeedback() {
        _actionState.update { it.copy(message = null, error = null) }
    }
}

data class OrderActionState(
    val inProgress: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState
    data class Success(val order: Order) : OrderDetailUiState
    data object Error : OrderDetailUiState
}
