package com.rebuildit.prestaflow.ui.products

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

/**
 * Pilote le flux "scan code-barres → recherche produit → ajustement stock" déclenché depuis
 * l'écran Produits (réception de marchandise).
 *
 * Étapes du flux :
 *  1. [onBarcodeScanned] : reçoit le code lu par ZXing, interroge `GET /products?barcode=`.
 *  2. Résultat unique → sélection auto ([selectedProduct]) et fiche stock prête à ajuster.
 *     Plusieurs résultats → l'UI affiche une liste de choix, [onSelectProduct] fixe la sélection.
 *     Aucun résultat → [ProductScanUiState.notFound].
 *  3. [onIncrement]/[onDecrement]/[onQuantityChange] ajustent la quantité saisie localement.
 *  4. [onConfirmAdjustment] envoie le PATCH stock (réutilise [ProductsRepository.updateStock]).
 */
@HiltViewModel
class ProductScanViewModel
    @Inject
    constructor(
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProductScanUiState())
        val uiState: StateFlow<ProductScanUiState> = _uiState.asStateFlow()

        /** Referme le flux et réinitialise tout l'état (aussi utilisé avant de relancer un scan). */
        fun onDismiss() {
            _uiState.value = ProductScanUiState()
        }

        fun onBarcodeScanned(code: String) {
            if (code.isBlank()) return
            viewModelScope.launch {
                _uiState.update {
                    ProductScanUiState(
                        isSheetVisible = true,
                        isLoading = true,
                        scannedCode = code,
                    )
                }
                runCatching { productsRepository.searchByBarcode(code) }
                    .onSuccess { results -> applyResults(code, results) }
                    .onFailure { error ->
                        Timber.w(error, "Barcode lookup failed for code=%s", code)
                        _uiState.update {
                            it.copy(isLoading = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        private fun applyResults(
            code: String,
            results: List<Product>,
        ) {
            _uiState.update { current ->
                when {
                    results.isEmpty() ->
                        current.copy(isLoading = false, results = emptyList(), notFound = true)
                    results.size == 1 ->
                        current.copy(
                            isLoading = false,
                            results = results,
                            selectedProduct = results.first(),
                            quantityInput = results.first().stock.quantity.toString(),
                        )
                    else -> current.copy(isLoading = false, results = results)
                }
            }
            Timber.d("Barcode lookup for code=%s returned %d result(s)", code, results.size)
        }

        fun onSelectProduct(product: Product) {
            _uiState.update {
                it.copy(selectedProduct = product, quantityInput = product.stock.quantity.toString())
            }
        }

        /** Repart du choix multiple (retour depuis la fiche stock vers la liste de résultats). */
        fun onBackToResults() {
            _uiState.update { it.copy(selectedProduct = null) }
        }

        fun onQuantityChange(value: String) {
            // On n'accepte que des chiffres — le champ reflète une quantité entière positive.
            if (value.isEmpty() || value.all { it.isDigit() }) {
                _uiState.update { it.copy(quantityInput = value) }
            }
        }

        fun onIncrement() {
            val current = _uiState.value.quantityInput.toIntOrNull() ?: 0
            _uiState.update { it.copy(quantityInput = (current + 1).toString()) }
        }

        fun onDecrement() {
            val current = _uiState.value.quantityInput.toIntOrNull() ?: 0
            val next = (current - 1).coerceAtLeast(0)
            _uiState.update { it.copy(quantityInput = next.toString()) }
        }

        fun onConfirmAdjustment() {
            val state = _uiState.value
            val product = state.selectedProduct ?: return
            val quantity = state.quantityInput.toIntOrNull() ?: return

            viewModelScope.launch {
                _uiState.update { it.copy(isSubmitting = true, error = null) }
                runCatching {
                    productsRepository.updateStock(
                        productId = product.id,
                        quantity = quantity,
                        warehouseId = product.stock.warehouseId,
                    )
                }
                    .onSuccess {
                        _uiState.update { it.copy(isSubmitting = false, submitSuccess = true) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to adjust stock for product %d", product.id)
                        _uiState.update {
                            it.copy(isSubmitting = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

data class ProductScanUiState(
    val isSheetVisible: Boolean = false,
    val isLoading: Boolean = false,
    val scannedCode: String? = null,
    val results: List<Product> = emptyList(),
    val notFound: Boolean = false,
    val selectedProduct: Product? = null,
    /** Quantité éditée sous forme texte pour coller au champ de saisie (vide = pas encore tapé). */
    val quantityInput: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val error: UiText? = null,
) {
    val hasMultipleResults: Boolean get() = results.size > 1 && selectedProduct == null
}
