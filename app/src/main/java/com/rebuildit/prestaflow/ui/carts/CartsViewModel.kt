package com.rebuildit.prestaflow.ui.carts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartsUiState(
    /** Liste complète chargée depuis l'API (déjà filtrée : paniers à 0 € exclus). */
    val allCarts: List<CartSummary> = emptyList(),
    val query: String = "",
    val displayedCount: Int = PAGE_SIZE,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
) {
    companion object {
        const val PAGE_SIZE = 25
    }

    /** Paniers correspondant à la recherche en cours. */
    private val matchingCarts: List<CartSummary>
        get() {
            val q = query.trim()
            return if (q.isEmpty()) {
                allCarts
            } else {
                allCarts.filter { cart ->
                    cart.customerName.contains(q, ignoreCase = true) ||
                        cart.customerEmail?.contains(q, ignoreCase = true) == true
                }
            }
        }

    /** Paniers à afficher (page courante). */
    val carts: List<CartSummary>
        get() = matchingCarts.take(displayedCount)

    /** Vrai si d'autres paniers peuvent être chargés (pagination). */
    val hasMore: Boolean
        get() = displayedCount < matchingCarts.size
}

@HiltViewModel
class CartsViewModel
    @Inject
    constructor(
        private val cartsRepository: CartsRepository,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CartsUiState())
        val uiState: StateFlow<CartsUiState> = _uiState

        init {
            load()
            observeActiveShopSwitch()
        }

        fun onRefresh() {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            viewModelScope.launch {
                runCatching { cartsRepository.getCarts() }
                    .onSuccess { carts ->
                        _uiState.update {
                            it.copy(
                                allCarts = carts.filter { c -> c.totalTaxIncl > 0 },
                                isRefreshing = false,
                            )
                        }
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

        fun onQueryChanged(query: String) {
            // Réinitialise la pagination à chaque changement de requête
            _uiState.update { it.copy(query = query, displayedCount = CartsUiState.PAGE_SIZE) }
        }

        fun loadMore() {
            _uiState.update { it.copy(displayedCount = it.displayedCount + CartsUiState.PAGE_SIZE) }
        }

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(
                                allCarts = emptyList(),
                                query = "",
                                displayedCount = CartsUiState.PAGE_SIZE,
                                isLoading = true,
                                error = null,
                            )
                        }
                        load()
                    }
            }
        }

        private fun load() {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                runCatching { cartsRepository.getCarts() }
                    .onSuccess { carts ->
                        _uiState.update {
                            it.copy(
                                // G — Filtre les paniers vides (total ≤ 0 €)
                                allCarts = carts.filter { c -> c.totalTaxIncl > 0 },
                                isLoading = false,
                            )
                        }
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
