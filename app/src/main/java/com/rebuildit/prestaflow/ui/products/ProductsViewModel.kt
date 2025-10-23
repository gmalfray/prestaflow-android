package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val productsRepository: ProductsRepository,
    private val networkErrorMapper: NetworkErrorMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductsUiState())
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    init {
        observeProducts()
        refresh(forceRemote = true, notifyOnError = false)
    }

    fun onRefresh() {
        refresh(forceRemote = true, notifyOnError = true)
    }

    private fun observeProducts() {
        viewModelScope.launch {
            productsRepository.observeProducts().collect { products ->
                _uiState.update { current ->
                    current.copy(
                        products = products,
                        isLoading = false,
                        isRefreshing = false,
                        error = if (products.isNotEmpty()) null else current.error
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
                    isLoading = current.products.isEmpty(),
                    error = if (notifyOnError) null else current.error
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
                            error = if (notifyOnError) mapped else current.error
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            isLoading = current.products.isEmpty(),
                            error = null
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
    val error: UiText? = null
)
