package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ProductDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
    ) : ViewModel() {
        private val productId: Long = checkNotNull(savedStateHandle["productId"])

        private val _uiState = MutableStateFlow(ProductDetailUiState())
        val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

        init {
            observeProduct()
            refreshProduct()
        }

        private fun observeProduct() {
            viewModelScope.launch {
                productsRepository.observeProduct(productId).collect { product ->
                    _uiState.update {
                        it.copy(
                            product = product,
                            // Ne quitte l'état de chargement que si le produit est arrivé, OU si le
                            // refresh réseau initial a déjà tranché (cf. refreshProduct().onFailure).
                            // Sinon, la 1ère émission Room (cache vide — produit jamais ouvert dans
                            // cette session) ferait flasher "introuvable" avant la vraie réponse
                            // réseau, alors que le produit existe bel et bien.
                            isLoading = it.isLoading && product == null,
                        )
                    }
                }
            }
        }

        private fun refreshProduct() {
            viewModelScope.launch {
                runCatching { productsRepository.refreshProduct(productId, forceRemote = true) }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh product $productId")
                        _uiState.update {
                            // Le refresh a tranché (échec) : si aucune donnée cache n'est arrivée
                            // entretemps, on sort de l'état de chargement pour révéler "introuvable"
                            // plutôt qu'un spinner infini.
                            it.copy(error = networkErrorMapper.map(error), isLoading = false)
                        }
                    }
            }
        }

        fun onUpdatePrice(newPrice: Double) {
            viewModelScope.launch {
                _uiState.update { it.copy(isUpdating = true) }
                runCatching { productsRepository.updatePrice(productId, newPrice) }
                    .onSuccess {
                        _uiState.update { state -> state.copy(isUpdating = false, error = null) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to update price")
                        _uiState.update { state ->
                            state.copy(isUpdating = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        fun onUpdateStock(newQuantity: Int) {
            viewModelScope.launch {
                _uiState.update { it.copy(isUpdating = true) }
                runCatching { productsRepository.updateStock(productId, newQuantity) }
                    .onSuccess {
                        _uiState.update { state -> state.copy(isUpdating = false, error = null) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to update stock")
                        _uiState.update { state ->
                            state.copy(isUpdating = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        fun onToggleStatus() {
            viewModelScope.launch {
                val currentProduct = _uiState.value.product ?: return@launch
                val newStatus = !currentProduct.active

                _uiState.update { it.copy(isUpdating = true) }
                runCatching { productsRepository.updateStatus(productId, newStatus) }
                    .onSuccess {
                        _uiState.update { state -> state.copy(isUpdating = false, error = null) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to update status")
                        _uiState.update { state ->
                            state.copy(isUpdating = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

data class ProductDetailUiState(
    val product: Product? = null,
    val isLoading: Boolean = true,
    val isUpdating: Boolean = false,
    val error: UiText? = null,
)
