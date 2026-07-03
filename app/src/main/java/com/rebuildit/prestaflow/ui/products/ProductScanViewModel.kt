package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
 *     Aucun résultat → [ProductScanUiState.notFound], avec une action [onStartAssociation] pour
 *     associer le code scanné à un produit existant (cas d'un produit sans EAN13 en base).
 *  3. [onIncrement]/[onDecrement]/[onQuantityChange] ajustent la quantité saisie localement.
 *  4. [onConfirmAdjustment] envoie le PATCH stock (réutilise [ProductsRepository.updateStock]).
 *
 * Flux d'association (déclenché depuis [ProductScanUiState.notFound]) :
 *  1. [onStartAssociation] ouvre la recherche produit ([ProductScanUiState.isAssociating]).
 *  2. [onAssociationQueryChange] pilote une recherche `GET /products?search=` debouncée
 *     (voir [observeAssociationQuery]), résultats dans [ProductScanUiState.associationResults].
 *  3. [onAssociationProductSelected] envoie `PATCH /products/{id}` avec le code scanné en EAN13
 *     ([ProductsRepository.setProductEan13]) puis enchaîne directement sur la fiche stock du
 *     produit désormais associé (comme un scan trouvé).
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class ProductScanViewModel
    @Inject
    constructor(
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
    ) : ViewModel() {
        private companion object {
            private const val ASSOCIATION_SEARCH_DEBOUNCE_MS = 300L
        }

        private val _uiState = MutableStateFlow(ProductScanUiState())
        val uiState: StateFlow<ProductScanUiState> = _uiState.asStateFlow()

        init {
            observeAssociationQuery()
        }

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
                            quantityInput = results.first().scannedQuantity.toString(),
                        )
                    else -> current.copy(isLoading = false, results = results)
                }
            }
            Timber.d("Barcode lookup for code=%s returned %d result(s)", code, results.size)
        }

        fun onSelectProduct(product: Product) {
            _uiState.update {
                it.copy(selectedProduct = product, quantityInput = product.scannedQuantity.toString())
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
                        combinationId = product.matchedCombination?.id,
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

        // ─── Association code-barres → produit existant (cas notFound) ─────────────────

        /** Ouvre la recherche produit pour associer le code scanné à un produit existant. */
        fun onStartAssociation() {
            _uiState.update {
                it.copy(
                    isAssociating = true,
                    associationQuery = "",
                    associationResults = emptyList(),
                    associationError = null,
                )
            }
        }

        /** Referme la recherche d'association et repart de l'état "aucun résultat". */
        fun onCancelAssociation() {
            _uiState.update {
                it.copy(
                    isAssociating = false,
                    associationQuery = "",
                    associationResults = emptyList(),
                    isAssociationSearching = false,
                    associationError = null,
                )
            }
        }

        fun onAssociationQueryChange(query: String) {
            _uiState.update { it.copy(associationQuery = query) }
        }

        /**
         * Observe [ProductScanUiState.associationQuery] avec un debounce de 300 ms (même pattern
         * que dans [ProductsViewModel]) pour déclencher `GET /products?search=` sans spammer le
         * serveur à chaque frappe.
         */
        private fun observeAssociationQuery() {
            viewModelScope.launch {
                _uiState
                    .map { it.associationQuery }
                    .distinctUntilChanged()
                    .debounce(ASSOCIATION_SEARCH_DEBOUNCE_MS)
                    .collect { query ->
                        if (!_uiState.value.isAssociating) return@collect
                        if (query.isBlank()) {
                            _uiState.update { it.copy(associationResults = emptyList(), isAssociationSearching = false) }
                            return@collect
                        }
                        _uiState.update { it.copy(isAssociationSearching = true) }
                        runCatching { productsRepository.searchProducts(query) }
                            .onSuccess { results ->
                                _uiState.update {
                                    it.copy(isAssociationSearching = false, associationResults = results)
                                }
                            }
                            .onFailure { error ->
                                Timber.w(error, "Association search failed for query=%s", query)
                                _uiState.update {
                                    it.copy(
                                        isAssociationSearching = false,
                                        associationError = networkErrorMapper.map(error),
                                    )
                                }
                            }
                    }
            }
        }

        /**
         * Associe le code scanné au [product] choisi, puis enchaîne directement sur la fiche
         * stock (comme un scan trouvé) en cas de succès.
         */
        fun onAssociationProductSelected(product: Product) {
            val code = _uiState.value.scannedCode ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isAssociationSubmitting = true, associationError = null) }
                runCatching { productsRepository.setProductEan13(product.id, code) }
                    .onSuccess { updated ->
                        Timber.d("Associated ean13=%s to product %d", code, updated.id)
                        _uiState.update {
                            it.copy(
                                isAssociating = false,
                                isAssociationSubmitting = false,
                                associationQuery = "",
                                associationResults = emptyList(),
                                associationError = null,
                                notFound = false,
                                results = listOf(updated),
                                selectedProduct = updated,
                                quantityInput = updated.stock.quantity.toString(),
                            )
                        }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to associate ean13=%s to product %d", code, product.id)
                        _uiState.update {
                            it.copy(
                                isAssociationSubmitting = false,
                                associationError = networkErrorMapper.map(error),
                            )
                        }
                    }
            }
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
    /** Recherche produit ouverte pour associer le code scanné (cas [notFound]). */
    val isAssociating: Boolean = false,
    val associationQuery: String = "",
    val associationResults: List<Product> = emptyList(),
    val isAssociationSearching: Boolean = false,
    val isAssociationSubmitting: Boolean = false,
    val associationError: UiText? = null,
) {
    val hasMultipleResults: Boolean get() = results.size > 1 && selectedProduct == null
}
