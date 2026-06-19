package com.rebuildit.prestaflow.ui.carts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.carts.model.CartDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartDetailUiState(
    val cart: CartDetail? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
class CartDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val cartsRepository: CartsRepository,
    ) : ViewModel() {
        private val cartId: Int = checkNotNull(savedStateHandle["cartId"])

        private val _uiState = MutableStateFlow(CartDetailUiState())
        val uiState: StateFlow<CartDetailUiState> = _uiState

        init {
            load()
        }

        private fun load() {
            _uiState.update { it.copy(isLoading = true, error = null) }
            viewModelScope.launch {
                runCatching { cartsRepository.getCartById(cartId) }
                    .onSuccess { cart ->
                        _uiState.update { it.copy(cart = cart, isLoading = false) }
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
