package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val ordersRepository: OrdersRepository,
    private val networkErrorMapper: NetworkErrorMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrdersUiState())
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    init {
        observeOrders()
        refresh(forceRemote = true, notifyOnError = false)
    }

    fun onRefresh() {
        refresh(forceRemote = true, notifyOnError = true)
    }

    private fun observeOrders() {
        viewModelScope.launch {
            ordersRepository.observeOrders().collect { orders ->
                _uiState.update { current ->
                    current.copy(
                        orders = orders,
                        isLoading = false,
                        isRefreshing = false,
                        error = if (orders.isNotEmpty()) null else current.error
                    )
                }
            }
        }
    }

    private fun refresh(forceRemote: Boolean, notifyOnError: Boolean) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isRefreshing = true,
                    isLoading = current.orders.isEmpty(),
                    error = if (notifyOnError) null else current.error
                )
            }

            runCatching { ordersRepository.refresh(forceRemote) }
                .onFailure { error ->
                    Timber.w(error, "Failed to refresh orders")
                    _uiState.update { current ->
                        val mapped = networkErrorMapper.map(error)
                        current.copy(
                            isRefreshing = false,
                            isLoading = current.orders.isEmpty(),
                            error = if (notifyOnError) mapped else current.error
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            isLoading = current.orders.isEmpty(),
                            error = null
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
    val error: UiText? = null
)
