package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    ordersRepository: OrdersRepository
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

    init {
        viewModelScope.launch {
            runCatching {
                ordersRepository.refreshOrder(orderId)
            }.onFailure {
                // Error handling can be improved, e.g. expose via a separate flow or UI state
                // For now, we rely on the local data or show loading/error state if empty
            }
        }
    }
}

sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState
    data class Success(val order: Order) : OrderDetailUiState
    data object Error : OrderDetailUiState
}
