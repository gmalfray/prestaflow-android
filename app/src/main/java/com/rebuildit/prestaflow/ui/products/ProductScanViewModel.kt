package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.toMatchedCombination
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
 *  2. Résultat unique :
 *     - le produit a une déclinaison [Product.matchedCombination] matchée par le scan → sélection
 *       auto ([selectedProduct]) et fiche stock de cette déclinaison, comme avant.
 *     - sinon, le produit porte ≥2 déclinaisons ([Product.combinations]) sans qu'aucune n'ait été
 *       désignée par le scan → [ProductScanUiState.needsCombinationChoice] affiche un sélecteur
 *       "Quelle déclinaison ?", [onSelectCombination] fixe le choix et bascule sur sa fiche stock.
 *     - sinon → sélection auto au niveau produit (comportement historique, inchangé).
 *     Plusieurs résultats (produits distincts) → l'UI affiche une liste de choix, [onSelectProduct]
 *     fixe la sélection.
 *     Aucun résultat → [ProductScanUiState.notFound], avec une action [onStartAssociation] pour
 *     associer le code scanné à un produit existant (cas d'un produit sans EAN13 en base).
 *  3. [onIncrement]/[onDecrement]/[onQuantityChange] ajustent la quantité saisie localement.
 *  4. [onConfirmAdjustment] envoie le PATCH stock (réutilise [ProductsRepository.updateStock]).
 *
 * Flux d'association (déclenché depuis [ProductScanUiState.notFound]) :
 *  1. [onStartAssociation] ouvre la recherche produit ([ProductScanUiState.isAssociating]).
 *  2. [onAssociationQueryChange] pilote une recherche `GET /products?search=` debouncée
 *     (voir [observeAssociationQuery]), résultats dans [ProductScanUiState.associationResults].
 *  3. [onAssociationProductSelected] choisit le produit auquel associer le code scanné :
 *     - 0 déclinaison → associe l'EAN13 au produit directement.
 *     - 1 déclinaison → l'associe automatiquement à cette déclinaison (son `combination_id`).
 *     - ≥2 déclinaisons → [ProductScanUiState.associationCombinationChoices] affiche un sélecteur
 *       "À quelle déclinaison associer ce code-barres ?", [onSelectAssociationCombination] fixe
 *       le choix.
 *     Dans tous les cas, envoie `PATCH /products/{id}` ([ProductsRepository.setProductEan13]) puis
 *     enchaîne directement sur la fiche stock du produit désormais associé (comme un scan trouvé).
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

            /** Seuil à partir duquel un choix explicite de déclinaison est nécessaire (en-dessous, pas d'ambiguïté). */
            private const val COMBINATION_CHOICE_THRESHOLD = 2
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

        /**
         * Point d'entrée quand [code] a déjà été recherché ailleurs (scanner permanent de réappro,
         * [StockReplenishViewModel]) et est connu introuvable : ouvre directement l'état
         * "introuvable" du sheet, sans relancer un 2ᵉ `GET /products?barcode=` du même code déjà
         * su vide côté [StockReplenishViewModel.onBarcodeScanned]. Produit exactement l'état que
         * [onBarcodeScanned] aurait atteint pour un résultat vide (`applyResults` avec
         * `results = emptyList()`), en sautant l'appel réseau et le passage par `isLoading = true`.
         */
        fun onKnownNotFound(code: String) {
            if (code.isBlank()) return
            _uiState.value = ProductScanUiState(isSheetVisible = true, scannedCode = code, notFound = true)
        }

        private fun applyResults(
            code: String,
            results: List<Product>,
        ) {
            _uiState.update { current ->
                when {
                    results.isEmpty() ->
                        current.copy(isLoading = false, results = emptyList(), notFound = true)
                    results.size == 1 -> applySingleResult(current, results.first())
                    else -> current.copy(isLoading = false, results = results)
                }
            }
            Timber.d("Barcode lookup for code=%s returned %d result(s)", code, results.size)
        }

        /**
         * Résout le résultat unique d'un scan : sélection directe si aucune ambiguïté
         * (déclinaison déjà matchée, ou produit à 0/1 déclinaison), sinon expose un sélecteur de
         * déclinaison ([ProductScanUiState.needsCombinationChoice]) — cf. KDoc de la classe.
         */
        private fun applySingleResult(
            current: ProductScanUiState,
            product: Product,
        ): ProductScanUiState =
            when {
                product.matchedCombination != null || product.combinations.size < COMBINATION_CHOICE_THRESHOLD ->
                    current.copy(
                        isLoading = false,
                        results = listOf(product),
                        selectedProduct = product,
                        quantityInput = product.scannedQuantity.toString(),
                    )
                else ->
                    current.copy(
                        isLoading = false,
                        results = listOf(product),
                        combinationChoices = product.combinations,
                    )
            }

        fun onSelectProduct(product: Product) {
            _uiState.update {
                it.copy(selectedProduct = product, quantityInput = product.scannedQuantity.toString())
            }
        }

        /**
         * Fixe la déclinaison choisie dans le sélecteur "Quelle déclinaison ?" (produit unique à
         * ≥2 déclinaisons, cf. [ProductScanUiState.needsCombinationChoice]) et ouvre sa fiche
         * stock, comme si le scan l'avait matchée directement.
         */
        fun onSelectCombination(combination: Combination) {
            val product = _uiState.value.results.firstOrNull() ?: return
            val updated = product.copy(matchedCombination = combination.toMatchedCombination())
            _uiState.update {
                it.copy(selectedProduct = updated, quantityInput = combination.quantity.toString())
            }
        }

        /**
         * Repart du choix multiple vers la liste de résultats précédente (autres produits, ou
         * déclinaisons du même produit si [ProductScanUiState.combinationChoices] est renseigné).
         */
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
                    associationCombinationChoices = emptyList(),
                    associationPendingProduct = null,
                )
            }
        }

        /**
         * Repart du sélecteur de déclinaison ("À quelle déclinaison associer ce code-barres ?")
         * vers la liste de résultats de recherche, sans perdre la requête tapée.
         */
        fun onCancelAssociationCombinationChoice() {
            _uiState.update {
                it.copy(associationCombinationChoices = emptyList(), associationPendingProduct = null)
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
         * Choisit le [product] auquel associer le code scanné :
         *  - 0 déclinaison → associe l'EAN13 au produit directement.
         *  - 1 déclinaison → l'associe automatiquement à cette déclinaison (pas d'ambiguïté).
         *  - ≥2 déclinaisons → expose [ProductScanUiState.associationCombinationChoices] pour
         *    laisser choisir laquelle recevra l'EAN13 ([onSelectAssociationCombination]).
         */
        fun onAssociationProductSelected(product: Product) {
            if (product.combinations.size >= COMBINATION_CHOICE_THRESHOLD) {
                _uiState.update {
                    it.copy(associationCombinationChoices = product.combinations, associationPendingProduct = product)
                }
                return
            }
            submitAssociation(product, product.combinations.singleOrNull()?.id)
        }

        /**
         * Fixe la déclinaison choisie dans le sélecteur d'association et soumet l'EAN13 avec son
         * `combination_id`.
         */
        fun onSelectAssociationCombination(combination: Combination) {
            val product = _uiState.value.associationPendingProduct ?: return
            submitAssociation(product, combination.id)
        }

        /**
         * Associe le code scanné au [product] (et, le cas échéant, à sa déclinaison
         * [combinationId]), puis enchaîne directement sur la fiche stock (comme un scan trouvé)
         * en cas de succès. En cas d'échec, le sélecteur de déclinaison (s'il était affiché) reste
         * ouvert pour permettre de réessayer.
         */
        private fun submitAssociation(
            product: Product,
            combinationId: Long?,
        ) {
            val code = _uiState.value.scannedCode ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isAssociationSubmitting = true, associationError = null) }
                runCatching { productsRepository.setProductEan13(product.id, code, combinationId) }
                    .onSuccess { updated ->
                        Timber.d(
                            "Associated ean13=%s to product %d (combinationId=%s)",
                            code,
                            updated.id,
                            combinationId,
                        )
                        _uiState.update {
                            it.copy(
                                isAssociating = false,
                                isAssociationSubmitting = false,
                                associationQuery = "",
                                associationResults = emptyList(),
                                associationCombinationChoices = emptyList(),
                                associationPendingProduct = null,
                                associationError = null,
                                notFound = false,
                            )
                        }
                        // Re-scanne le code désormais associé : le produit revient combinaison-aware
                        // (matched_combination + quantité de la déclinaison), pour que la fiche stock
                        // cible la bonne déclinaison dès le 1er ajustement (sinon le 1er update partait
                        // au niveau produit, d'où « pas mis à jour au 1er essai »).
                        val refreshed = runCatching { productsRepository.searchByBarcode(code) }.getOrNull()
                        if (!refreshed.isNullOrEmpty()) {
                            applyResults(code, refreshed)
                        } else {
                            _uiState.update {
                                it.copy(
                                    results = listOf(updated),
                                    selectedProduct = updated,
                                    quantityInput = updated.scannedQuantity.toString(),
                                )
                            }
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
    /**
     * Déclinaisons à choisir quand le scan a matché un unique produit à ≥2 déclinaisons sans en
     * désigner une précisément (cf. [ProductScanViewModel.onSelectCombination]). Le produit
     * concerné est [results] (résultat unique dans ce cas).
     */
    val combinationChoices: List<Combination> = emptyList(),
    /** Recherche produit ouverte pour associer le code scanné (cas [notFound]). */
    val isAssociating: Boolean = false,
    val associationQuery: String = "",
    val associationResults: List<Product> = emptyList(),
    val isAssociationSearching: Boolean = false,
    val isAssociationSubmitting: Boolean = false,
    val associationError: UiText? = null,
    /**
     * Déclinaisons à choisir pour savoir laquelle recevra l'EAN13 scanné, quand le produit
     * sélectionné pour l'association ([associationPendingProduct]) a ≥2 déclinaisons (cf.
     * [ProductScanViewModel.onSelectAssociationCombination]).
     */
    val associationCombinationChoices: List<Combination> = emptyList(),
    /** Produit en attente d'un choix de déclinaison pour l'association (cf. [associationCombinationChoices]). */
    val associationPendingProduct: Product? = null,
) {
    val hasMultipleResults: Boolean get() = results.size > 1 && selectedProduct == null

    /** Sélecteur "Quelle déclinaison ?" à afficher pour l'ajustement de stock. */
    val needsCombinationChoice: Boolean get() = combinationChoices.isNotEmpty() && selectedProduct == null
}
