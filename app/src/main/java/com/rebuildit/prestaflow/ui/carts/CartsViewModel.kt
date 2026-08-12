package com.rebuildit.prestaflow.ui.carts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
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
    // Vrai par défaut (comme les autres UiState d'écran) : le 1er rendu doit afficher le loader
    // tant que load() n'a pas mis à jour l'état, jamais un flash "vide" avant le 1er chargement.
    val isLoading: Boolean = true,
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
        private val resumeRefreshGuard: ScreenResumeRefreshGuard,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CartsUiState())
        val uiState: StateFlow<CartsUiState> = _uiState

        init {
            load()
            observeActiveShopSwitch()
        }

        fun onRefresh() = refreshCarts(notifyOnError = true)

        /**
         * Rattrapage au retour sur l'écran Paniers (cf. KDoc de
         * [com.rebuildit.prestaflow.ui.orders.OrdersViewModel.onScreenResumed] pour le contrat
         * général implémenté par [resumeRefreshGuard]). La recherche et la pagination
         * ([CartsUiState.query], [CartsUiState.displayedCount]) sont filtrées EN LOCAL depuis
         * [CartsUiState.allCarts] (cf. [CartsUiState.carts]) : recharger [allCarts] les préserve
         * automatiquement, sans rien à repasser en paramètre. Silencieux en cas d'échec.
         */
        fun onScreenResumed() {
            val current = _uiState.value
            if (!resumeRefreshGuard.shouldRefresh(isBusy = current.isLoading || current.isRefreshing)) return
            refreshCarts(notifyOnError = false)
        }

        private fun refreshCarts(notifyOnError: Boolean) {
            _uiState.update { it.copy(isRefreshing = true, error = if (notifyOnError) null else it.error) }
            viewModelScope.launch {
                runCatching { cartsRepository.getCarts() }
                    .onSuccess { carts ->
                        _uiState.update {
                            it.copy(
                                allCarts = carts.filter { c -> c.totalTaxIncl > 0 },
                                isRefreshing = false,
                            )
                        }
                        resumeRefreshGuard.markRefreshSucceeded()
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                error = if (notifyOnError) UiText.Dynamic(error.message ?: "Unknown error") else it.error,
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
                        resumeRefreshGuard.markRefreshSucceeded()
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
