package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
        }

        fun onRefresh() {
            refresh(forceRemote = true, notifyOnError = true)
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(products = emptyList(), isLoading = true, error = null)
                        }
                        refresh(forceRemote = true, notifyOnError = true)
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

                runCatching { productsRepository.refresh(forceRemote) }
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
                    .onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.products.isEmpty(),
                                error = null,
                            )
                        }
                    }
            }
        }
    }

data class ProductsUiState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
) {
    /** Liste filtrée par [query] sur le nom et la référence (insensible à la casse). */
    val visibleProducts: List<Product>
        get() =
            if (query.isBlank()) {
                products
            } else {
                products.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.reference.contains(query, ignoreCase = true)
                }
            }
}
