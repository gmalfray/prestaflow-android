package com.rebuildit.prestaflow.ui.carts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartsUiState(
    val carts: List<CartSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class CartsViewModel
    @Inject
    constructor(
        private val cartsRepository: CartsRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CartsUiState())
        val uiState: StateFlow<CartsUiState> = _uiState

        init {
            load()
        }

        fun onRefresh() {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            viewModelScope.launch {
                runCatching { cartsRepository.getCarts() }
                    .onSuccess { carts ->
                        _uiState.update { it.copy(carts = carts, isRefreshing = false) }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                error = UiText.Dynamic(error.message ?: "Unknown error"),
                            )
                        }
                    }
            }
        }

        private fun load() {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                runCatching { cartsRepository.getCarts() }
                    .onSuccess { carts ->
                        _uiState.update { it.copy(carts = carts, isLoading = false) }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = UiText.Dynamic(error.message ?: "Unknown error"),
                            )
                        }
                    }
            }
        }
    }
