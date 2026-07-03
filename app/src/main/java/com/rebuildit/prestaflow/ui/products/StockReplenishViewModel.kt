package com.rebuildit.prestaflow.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.StockReplenishPreferencesRepository
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.toMatchedCombination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Durée (ms) de la fenêtre « Annuler » avant l'envoi effectif d'un ajustement de stock, une fois
 * « Valider » tapé. Même pattern/valeur que [com.rebuildit.prestaflow.ui.orders.SWIPE_UNDO_DELAY_MS]
 * (décompte swipe commandes), réutilisé ici pour l'écran de réappro série.
 */
internal const val REPLENISH_UNDO_DELAY_MS = 10_000L

/** Seuil à partir duquel un choix explicite de déclinaison est nécessaire (cf. [ProductScanViewModel]). */
private const val COMBINATION_CHOICE_THRESHOLD = 2

/**
 * Pilote l'écran « Ajout / réappro stock » (Lot 1) : scanner permanent → produit résolu → delta
 * accumulé via boutons rapides ([quickAddAmounts], configurables en préférences depuis le Lot 2 —
 * défaut [DEFAULT_QUICK_ADD_AMOUNTS] = +5/+10/+20) et saisie libre → écriture unique via
 * [onValidate], avec fenêtre d'annulation de [REPLENISH_UNDO_DELAY_MS] ms (même pattern que le swipe commandes,
 * cf. [com.rebuildit.prestaflow.ui.orders.OrdersViewModel.onSwipeAction]).
 *
 * Remplace le flux historique "scan → fiche stock" ([ProductScanViewModel]) pour l'ajustement d'un
 * produit CONNU (EAN déjà associé). Le flux d'association d'un EAN INCONNU reste porté par
 * [ProductScanViewModel] (inchangé) : [StockReplenishScreen] y délègue quand [onBarcodeScanned]
 * expose [StockReplenishUiState.notFound], puis récupère le produit résolu via
 * [onProductResolvedExternally] une fois l'association terminée.
 *
 * Combinaison-aware comme [ProductScanViewModel] : un scan matchant ≥2 déclinaisons sans en désigner
 * une précisément expose [StockReplenishUiState.combinationChoices] ([onSelectCombination] pour
 * trancher) ; une combinaison déjà matchée ou un produit à 0/1 déclinaison résout directement.
 *
 * Réappro en série : [onValidate] réarme IMMÉDIATEMENT le scanner (l'écriture réelle part en tâche
 * de fond après le délai d'annulation) — plusieurs écritures peuvent donc être en attente en
 * parallèle ([StockReplenishUiState.pendingWrites], chacune annulable indépendamment via
 * [onCancelPendingWrite]), contrairement au swipe commandes où une nouvelle action remplace la
 * précédente. Choix assumé : annuler une écriture en attente la retire simplement de la file (pas de
 * retour à un état éditable) — au delà de ce MVP, un flux plus riche (ré-ouvrir l'édition) relèverait
 * d'un lot ultérieur.
 */
@HiltViewModel
class StockReplenishViewModel
    @Inject
    constructor(
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        stockReplenishPreferencesRepository: StockReplenishPreferencesRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(StockReplenishUiState())
        val uiState: StateFlow<StockReplenishUiState> = _uiState.asStateFlow()

        /**
         * Montants des boutons rapides (Lot 2 — configurables en préférences, cf.
         * [com.rebuildit.prestaflow.ui.settings.StockReplenishPrefsViewModel]). Défaut
         * [DEFAULT_QUICK_ADD_AMOUNTS] tant que rien n'est chargé/configuré (comportement Lot 1).
         */
        val quickAddAmounts: StateFlow<List<Int>> =
            stockReplenishPreferencesRepository.quickAddAmounts
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DEFAULT_QUICK_ADD_AMOUNTS,
                )

        /** Jobs des écritures en attente (fenêtre d'annulation), indexés par [PendingStockWrite.id]. */
        private val pendingJobs = mutableMapOf<String, Job>()
        private var pendingSeq = 0L

        // ─── Scan ────────────────────────────────────────────────────────────

        /**
         * Reçoit un code lu par le scanner permanent. Ignoré si le scanner n'est pas censé être
         * actif (produit déjà affiché, choix en attente…) — filet de sécurité en plus de la pause
         * caméra pilotée côté UI par [StockReplenishUiState.isScannerActive].
         */
        fun onBarcodeScanned(code: String) {
            if (code.isBlank()) return
            if (!_uiState.value.isScannerActive) return
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isLookupLoading = true, scannedCode = code, notFound = false, error = null)
                }
                runCatching { productsRepository.searchByBarcode(code) }
                    .onSuccess { results -> applyResults(results) }
                    .onFailure { error ->
                        Timber.w(error, "Barcode lookup failed for code=%s", code)
                        _uiState.update {
                            it.copy(isLookupLoading = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        private fun applyResults(results: List<Product>) {
            _uiState.update { current ->
                when {
                    results.isEmpty() -> current.copy(isLookupLoading = false, notFound = true)
                    results.size == 1 -> applySingleResult(current, results.first())
                    else -> current.copy(isLookupLoading = false, multipleResults = results)
                }
            }
        }

        private fun applySingleResult(
            current: StockReplenishUiState,
            product: Product,
        ): StockReplenishUiState =
            when {
                product.matchedCombination != null || product.combinations.size < COMBINATION_CHOICE_THRESHOLD ->
                    current.copy(isLookupLoading = false, product = product, delta = 0, quantityInput = "")
                else ->
                    current.copy(
                        isLookupLoading = false,
                        combinationChoices = product.combinations,
                        scannedProductForChoice = product,
                    )
            }

        /** Choix parmi plusieurs produits distincts matchés par le même code (rare). */
        fun onSelectFromMultipleResults(product: Product) {
            _uiState.update { applySingleResult(it.copy(multipleResults = emptyList()), product) }
        }

        /** Fixe la déclinaison choisie dans le sélecteur "Quelle déclinaison ?". */
        fun onSelectCombination(combination: Combination) {
            val product = _uiState.value.scannedProductForChoice ?: return
            val updated = product.copy(matchedCombination = combination.toMatchedCombination())
            _uiState.update {
                it.copy(
                    combinationChoices = emptyList(),
                    scannedProductForChoice = null,
                    product = updated,
                    delta = 0,
                    quantityInput = "",
                )
            }
        }

        /**
         * Reçoit le produit résolu par le flux d'association d'un EAN inconnu, porté par
         * [ProductScanViewModel] (réutilisé tel quel par [StockReplenishScreen] pour ce sous-flux).
         * Bascule directement sur la fiche d'ajustement, comme un scan trouvé.
         */
        fun onProductResolvedExternally(product: Product) {
            _uiState.update {
                it.copy(notFound = false, scannedCode = null, product = product, delta = 0, quantityInput = "")
            }
        }

        // ─── Accumulation du delta ──────────────────────────────────────────

        /** Bouton rapide fixe (+5 / +10 / +20) : s'accumule dans le delta affiché, rien n'est écrit. */
        fun onQuickAdd(amount: Int) {
            _uiState.update { it.copy(delta = it.delta + amount) }
        }

        fun onQuantityInputChange(value: String) {
            if (value.isEmpty() || value.all { it.isDigit() }) {
                _uiState.update { it.copy(quantityInput = value) }
            }
        }

        /** Ajoute la quantité saisie librement au delta accumulé — même logique que [onQuickAdd]. */
        fun onAddTypedQuantity() {
            val amount = _uiState.value.quantityInput.toIntOrNull() ?: return
            if (amount <= 0) return
            _uiState.update { it.copy(delta = it.delta + amount, quantityInput = "") }
        }

        /** Repart de zéro sans perdre le produit affiché (erreur de saisie). */
        fun onResetDelta() {
            _uiState.update { it.copy(delta = 0) }
        }

        /** Passe au suivant sans valider d'ajustement pour l'article affiché (aucune écriture). */
        fun onSkip() {
            _uiState.update { StockReplenishUiState(pendingWrites = it.pendingWrites) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        // ─── Validation (écriture différée avec fenêtre d'annulation) ───────

        /**
         * Valide l'ajustement accumulé : réarme IMMÉDIATEMENT le scanner (retour à l'état de scan,
         * pour enchaîner sur l'article suivant) et programme l'écriture réelle après
         * [REPLENISH_UNDO_DELAY_MS] ms, annulable entre-temps via [onCancelPendingWrite].
         */
        fun onValidate() {
            val state = _uiState.value
            val product = state.product ?: return
            if (state.delta == 0) return

            val pending =
                PendingStockWrite(
                    id = "write-${pendingSeq++}",
                    productId = product.id,
                    combinationId = product.matchedCombination?.id,
                    warehouseId = product.stock.warehouseId,
                    productName =
                        product.matchedCombination?.let { "${product.name} — ${it.name}" } ?: product.name,
                    delta = state.delta,
                    newQuantity = product.scannedQuantity + state.delta,
                )

            _uiState.update { StockReplenishUiState(pendingWrites = it.pendingWrites + pending) }

            pendingJobs[pending.id] =
                viewModelScope.launch {
                    delay(REPLENISH_UNDO_DELAY_MS)
                    _uiState.update { it.copy(pendingWrites = it.pendingWrites.filterNot { w -> w.id == pending.id }) }
                    pendingJobs.remove(pending.id)
                    runCatching {
                        productsRepository.updateStock(
                            productId = pending.productId,
                            quantity = pending.newQuantity,
                            warehouseId = pending.warehouseId,
                            combinationId = pending.combinationId,
                        )
                    }.onFailure { error ->
                        Timber.w(error, "Failed to write stock replenishment for product %d", pending.productId)
                        _uiState.update {
                            it.copy(
                                writeErrorMessage =
                                    "Échec de la mise à jour de ${pending.productName} : " +
                                        "le stock n'a pas été modifié",
                            )
                        }
                    }
                }
        }

        /** Annule une écriture en attente (fenêtre des [REPLENISH_UNDO_DELAY_MS] ms) : aucun appel API. */
        fun onCancelPendingWrite(id: String) {
            pendingJobs.remove(id)?.cancel()
            _uiState.update { it.copy(pendingWrites = it.pendingWrites.filterNot { w -> w.id == id }) }
        }

        fun consumeWriteError() {
            _uiState.update { it.copy(writeErrorMessage = null) }
        }
    }

/** Écriture de stock en attente d'envoi (fenêtre d'annulation), cf. [StockReplenishViewModel.onValidate]. */
data class PendingStockWrite(
    val id: String,
    val productId: Long,
    val combinationId: Long?,
    val warehouseId: Long?,
    val productName: String,
    val delta: Int,
    val newQuantity: Int,
)

data class StockReplenishUiState(
    val isLookupLoading: Boolean = false,
    val scannedCode: String? = null,
    /** Produit ciblé par l'ajustement courant (résolu par scan direct, choix ou association). */
    val product: Product? = null,
    /** Produits distincts matchés par le même code (rare) — choix requis avant résolution. */
    val multipleResults: List<Product> = emptyList(),
    /** Déclinaisons à choisir quand le scan matche un produit à ≥2 déclinaisons sans en désigner une. */
    val combinationChoices: List<Combination> = emptyList(),
    /** Produit en attente d'un choix de déclinaison (cf. [combinationChoices]). */
    val scannedProductForChoice: Product? = null,
    /** Aucun produit ne correspond au code scanné — délégué au flux d'association ([ProductScanViewModel]). */
    val notFound: Boolean = false,
    /** Delta accumulé (boutons rapides + saisie libre), pas encore écrit. */
    val delta: Int = 0,
    val quantityInput: String = "",
    val error: UiText? = null,
    /** Écritures validées, en attente d'envoi (fenêtre d'annulation), potentiellement plusieurs en parallèle. */
    val pendingWrites: List<PendingStockWrite> = emptyList(),
    /** Message d'échec d'une écriture en tâche de fond (fenêtre d'annulation écoulée), à consommer. */
    val writeErrorMessage: String? = null,
) {
    /** Le scanner permanent doit être actif (caméra allumée) seulement dans cet état. */
    val isScannerActive: Boolean
        get() =
            !isLookupLoading && product == null && combinationChoices.isEmpty() &&
                multipleResults.isEmpty() && !notFound

    /** Stock résultant si le delta accumulé était validé maintenant. */
    val newQuantity: Int get() = (product?.scannedQuantity ?: 0) + delta

    val canValidate: Boolean get() = product != null && delta != 0
}
