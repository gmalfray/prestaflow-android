package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class ProductsViewModel
    @Inject
    constructor(
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProductsUiState())
        val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

        init {
            observeProducts()
            refresh(forceRemote = true, notifyOnError = false)
            observeActiveShopSwitch()
            observeSearchQuery()
        }

        fun onRefresh() {
            refresh(forceRemote = true, notifyOnError = true)
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        /** Sélectionne un filtre de stock puis recharge la liste depuis le serveur. */
        fun onStockFilterSelected(filter: StockFilter) {
            _uiState.update { it.copy(stockFilter = filter) }
            refresh(forceRemote = true, notifyOnError = true)
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
                                products = emptyList(),
                                totalCount = 0,
                                isLoading = true,
                                error = null,
                                stockFilter = StockFilter.ALL,
                            )
                        }
                        refresh(forceRemote = true, notifyOnError = true)
                    }
            }
        }

        /**
         * Observe les changements de query avec un debounce de 300 ms pour déclencher
         * une recherche API sans spammer le serveur à chaque frappe.
         */
        private fun observeSearchQuery() {
            viewModelScope.launch {
                _uiState
                    .map { it.query }
                    .distinctUntilChanged()
                    .drop(1) // ignore la valeur initiale vide
                    .debounce(300L)
                    .collect {
                        refresh(forceRemote = true, notifyOnError = false)
                    }
            }
        }

        private fun observeProducts() {
            viewModelScope.launch {
                productsRepository.observeProducts().collect { products ->
                    _uiState.update { current ->
                        current.copy(
                            products = products,
                            isLoading = false,
                            isRefreshing = false,
                            error = if (products.isNotEmpty()) null else current.error,
                        )
                    }
                }
            }
        }

        private fun refresh(
            forceRemote: Boolean,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.products.isEmpty(),
                        error = if (notifyOnError) null else current.error,
                    )
                }

                val stockFilter = _uiState.value.stockFilter.apiValue
                val search = _uiState.value.query.takeIf { it.isNotBlank() }
                runCatching { productsRepository.refresh(forceRemote, stockFilter, search) }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh products")
                        _uiState.update { current ->
                            val mapped = networkErrorMapper.map(error)
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.products.isEmpty(),
                                error = if (notifyOnError) mapped else current.error,
                            )
                        }
                    }
                    .onSuccess { total ->
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.products.isEmpty(),
                                error = null,
                                totalCount = total ?: current.totalCount,
                            )
                        }
                    }
            }
        }
    }

data class ProductsUiState(
    val products: List<Product> = emptyList(),
    /**
     * Total réel rapporté par l'API (tient compte des filtres actifs et de la recherche).
     * Vaut 0 tant que le premier refresh n'a pas abouti.
     */
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    /** Filtre de stock actif. */
    val stockFilter: StockFilter = StockFilter.ALL,
) {
    /**
     * La recherche est déléguée à l'API : [products] contient déjà les résultats filtrés
     * par le serveur. Pas de filtrage local supplémentaire.
     */
    val visibleProducts: List<Product> get() = products
}
