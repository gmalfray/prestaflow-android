package com.rebuildit.prestaflow.ui.products

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ocr.LabelReferenceParser
import com.rebuildit.prestaflow.core.ocr.LabelTextRecognizer
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.products.ProductsRepository
import com.rebuildit.prestaflow.domain.products.StockReplenishPreferencesRepository
import com.rebuildit.prestaflow.domain.products.model.Combination
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.toMatchedCombination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
 * Fenêtre (ms) pendant laquelle un second scan du MÊME code est considéré comme un doublon
 * immédiat (décodage continu zxing qui relit plusieurs fois le même code avant que l'état
 * `isScannerActive` ne bascule à `false`) — ignoré silencieusement (cf. Lot 3, retour
 * haptique/sonore : « pas de doublon immédiat du même code »). Volontairement court : ne doit pas
 * gêner un enchaînement légitime (re-scanner le même article après Valider/Passer).
 */
private const val DUPLICATE_SCAN_WINDOW_MS = 1_200L

/**
 * Délai maximum accordé à la tentative de secours OCR (lecture d'étiquette, cf. KDoc classe,
 * section OCR) — OCR + recherche produit sur les jetons candidats. Volontairement COURT et DUR :
 * l'objectif de la fonctionnalité est de faire gagner du temps, elle ne doit donc JAMAIS ajouter de
 * latence perçue. Au delà, retombe silencieusement sur l'association manuelle (comportement
 * historique), sans attendre davantage.
 */
private const val LABEL_FALLBACK_TIMEOUT_MS = 1_300L

/** Nombre max de jetons candidats ([LabelReferenceParser]) recherchés en parallèle (cf. contrainte vitesse). */
private const val MAX_LABEL_SEARCH_TOKENS = 4

/** Nombre max de produits suggérés au final (union dédupliquée des recherches par jeton). */
private const val MAX_LABEL_SUGGESTIONS = 8

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
 *
 * **Lot 3 (polish du scan en série)** :
 * - [scanFeedbackEvents] : événement one-shot consommé côté écran pour déclencher un retour
 *   haptique ([androidx.compose.ui.platform.LocalHapticFeedback], toujours actif — respecte déjà le
 *   réglage système de vibration au toucher) + un bip sonore si [soundOnScan] est activé. Émis dès
 *   qu'un scan aboutit à un résultat exploitable (produit direct, choix de déclinaisons ou choix
 *   parmi plusieurs produits), PAS sur code introuvable ni sur erreur réseau. Un doublon immédiat du
 *   même code (cf. [DUPLICATE_SCAN_WINDOW_MS]) est filtré en amont dans [onBarcodeScanned] : ni
 *   lookup, ni feedback.
 * - [StockReplenishUiState.sessionRecap] : compteur courant (nb d'articles + unités) des ajustements
 *   *réellement* validés de la session — incrémenté de façon optimiste dès [onValidate] (l'utilisateur
 *   a tapé « Valider », l'écriture est engagée), décrémenté si annulé via [onCancelPendingWrite]
 *   pendant la fenêtre, et décrémenté également si l'écriture différée échoue en tâche de fond (le
 *   stock n'a alors pas réellement changé, cf. message d'erreur existant). Traverse les resets
 *   d'état complet d'[onValidate]/[onSkip] (qui reconstruisent un [StockReplenishUiState] neuf pour
 *   repartir sur l'article suivant) : explicitement recopié à chaque fois.
 * - [StockReplenishUiState.queueAddedTick] : compteur incrémenté à chaque [onValidate] réussi,
 *   consommé côté écran pour déclencher une confirmation visuelle discrète (coche qui apparaît
 *   brièvement) sans dépendre d'un `Boolean` qui ne changerait pas d'une validation à l'autre si
 *   l'utilisateur enchaîne trop vite pour qu'un `LaunchedEffect` recompose entre deux.
 *
 * **Secours OCR (v0.38.0)** : quand un scan ne matche AUCUN produit, [onBarcodeScanned] tente en
 * plus (best-effort, jamais bloquant) de lire le texte de l'étiquette AVANT de retomber sur
 * l'association manuelle à vide :
 *  1. [frameProvider][onBarcodeScanned] réutilise la frame caméra qui a servi au décodage du
 *     code-barres (exposée par zxing via `BarcodeResult.getBitmap()`, cf. [StockReplenishScreen])
 *     — pas de capture séparée. Passée en `() -> Bitmap?` (PAS un `Bitmap` déjà calculé) : zxing
 *     documente cette conversion YUV→Bitmap comme potentiellement coûteuse, autant ne la payer QUE
 *     sur ce chemin rare (EAN introuvable), jamais sur le chemin chaud (code trouvé du 1er coup).
 *  2. [attemptLabelFallback] : si aucune frame n'est fournie (capture indisponible), retombe
 *     immédiatement sur `notFound` — sinon lance [findCandidatesFromLabel] sous un timeout DUR
 *     [LABEL_FALLBACK_TIMEOUT_MS] ([StockReplenishUiState.isLabelSearchLoading] pendant la
 *     tentative, scanner en pause). Passé le délai (ou en cas d'échec/OCR illisible/aucun match),
 *     retombe SILENCIEUSEMENT sur `notFound` sans suggestion — comportement historique inchangé.
 *  3. [findCandidatesFromLabel] : OCR via [labelTextRecognizer] → jetons candidats via
 *     [LabelReferenceParser] (pure, testée isolément) → recherche produit existante
 *     ([ProductsRepository.searchProducts]) lancée EN PARALLÈLE sur chaque jeton (plafonné à
 *     [MAX_LABEL_SEARCH_TOKENS]) → union dédupliquée des résultats
 *     ([StockReplenishUiState.labelSuggestions], plafonnée à [MAX_LABEL_SUGGESTIONS]).
 *  4. Si des suggestions sont trouvées, [StockReplenishScreen] les transmet à
 *     [ProductScanViewModel.onKnownNotFoundWithSuggestions] : réutilise TEL QUEL le flux
 *     d'association existant (recherche pré-remplie plutôt que vide) — associer l'EAN à un
 *     candidat suit exactement le chemin manuel habituel (PATCH ean13 + enchaînement fiche stock).
 */
@HiltViewModel
class StockReplenishViewModel
    @Inject
    constructor(
        private val productsRepository: ProductsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val labelTextRecognizer: LabelTextRecognizer,
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

        /** Activation du bip sonore de confirmation au scan (Lot 3), défaut activé. */
        val soundOnScan: StateFlow<Boolean> =
            stockReplenishPreferencesRepository.soundOnScan
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = true,
                )

        private val _scanFeedbackEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Événement one-shot : un scan vient d'aboutir à un résultat exploitable (cf. KDoc classe). */
        val scanFeedbackEvents: SharedFlow<Unit> = _scanFeedbackEvents.asSharedFlow()

        /** Jobs des écritures en attente (fenêtre d'annulation), indexés par [PendingStockWrite.id]. */
        private val pendingJobs = mutableMapOf<String, Job>()
        private var pendingSeq = 0L

        /** Dédup des doublons immédiats de scan (cf. [DUPLICATE_SCAN_WINDOW_MS]). */
        private var lastScanCode: String? = null
        private var lastScanAtMs: Long = 0L

        // ─── Scan ────────────────────────────────────────────────────────────

        /**
         * Reçoit un code lu par le scanner permanent. Ignoré si le scanner n'est pas censé être
         * actif (produit déjà affiché, choix en attente…) — filet de sécurité en plus de la pause
         * caméra pilotée côté UI par [StockReplenishUiState.isScannerActive]. Ignoré également si
         * c'est un doublon immédiat du même code (cf. [DUPLICATE_SCAN_WINDOW_MS]).
         *
         * @param frameProvider Fournit paresseusement la frame caméra ayant servi au décodage (cf.
         * KDoc classe, section OCR) — un `() -> Bitmap?` plutôt qu'un `Bitmap` déjà calculé : la
         * conversion n'est déclenchée que si le code s'avère introuvable (chemin rare), jamais sur
         * le chemin chaud d'un code trouvé du premier coup. `{ null }` par défaut (aucune capture
         * disponible) : le secours OCR est alors sauté, retombe directement sur `notFound`.
         */
        fun onBarcodeScanned(
            code: String,
            frameProvider: () -> Bitmap? = { null },
        ) {
            val now = System.currentTimeMillis()
            val isDuplicateScan = code == lastScanCode && now - lastScanAtMs < DUPLICATE_SCAN_WINDOW_MS
            if (code.isBlank() || !_uiState.value.isScannerActive || isDuplicateScan) return
            lastScanCode = code
            lastScanAtMs = now
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLookupLoading = true,
                        scannedCode = code,
                        notFound = false,
                        labelSuggestions = emptyList(),
                        error = null,
                    )
                }
                runCatching { productsRepository.searchByBarcode(code) }
                    .onSuccess { results -> applyResults(code, results, frameProvider) }
                    .onFailure { error ->
                        Timber.w(error, "Barcode lookup failed for code=%s", code)
                        _uiState.update {
                            it.copy(isLookupLoading = false, error = networkErrorMapper.map(error))
                        }
                    }
            }
        }

        private fun applyResults(
            code: String,
            results: List<Product>,
            frameProvider: () -> Bitmap?,
        ) {
            if (results.isEmpty()) {
                attemptLabelFallback(code, frameProvider)
                return
            }
            // Détecté avec succès (produit direct, choix de déclinaisons ou choix multiple) :
            // feedback haptique/sonore, jamais sur échec (cf. KDoc classe).
            _scanFeedbackEvents.tryEmit(Unit)
            _uiState.update { current ->
                if (results.size == 1) {
                    applySingleResult(current, results.first())
                } else {
                    current.copy(isLookupLoading = false, multipleResults = results)
                }
            }
        }

        /**
         * EAN introuvable : tente le secours OCR (cf. KDoc classe) avant de retomber sur
         * l'association manuelle. Best-effort STRICT : timeout dur [LABEL_FALLBACK_TIMEOUT_MS] —
         * au delà, ou si [frameProvider] ne fournit aucune frame, ou si rien n'est trouvé, retombe
         * SILENCIEUSEMENT sur `notFound` sans suggestion (comportement historique), sans jamais
         * retarder l'utilisateur au delà de ce délai.
         */
        private fun attemptLabelFallback(
            code: String,
            frameProvider: () -> Bitmap?,
        ) {
            val frame = runCatching { frameProvider() }.getOrNull()
            if (frame == null) {
                _uiState.update { it.copy(isLookupLoading = false, notFound = true) }
                return
            }
            _uiState.update { it.copy(isLookupLoading = false, isLabelSearchLoading = true) }
            viewModelScope.launch {
                val suggestions =
                    withTimeoutOrNull(LABEL_FALLBACK_TIMEOUT_MS) { findCandidatesFromLabel(frame) }.orEmpty()
                // L'utilisateur a pu enchaîner sur un autre scan / quitter l'état d'attente entre
                // temps (ex. onSkip) — n'applique le résultat que si on attend toujours CE code.
                if (_uiState.value.scannedCode != code || !_uiState.value.isLabelSearchLoading) return@launch
                _uiState.update { it.copy(isLabelSearchLoading = false, notFound = true, labelSuggestions = suggestions) }
            }
        }

        /**
         * OCR de [frame] ([labelTextRecognizer]) → jetons candidats ([LabelReferenceParser]) →
         * recherche produit EN PARALLÈLE sur chacun (plafonné à [MAX_LABEL_SEARCH_TOKENS], cf.
         * contrainte vitesse — pas de recherches en série inutiles) → union dédupliquée des
         * résultats, plafonnée à [MAX_LABEL_SUGGESTIONS]. Ni l'OCR ni la recherche ne remontent
         * d'exception (best-effort) : une étape en échec retourne simplement une liste vide.
         */
        private suspend fun findCandidatesFromLabel(frame: Bitmap): List<Product> {
            val text = runCatching { labelTextRecognizer.recognize(frame) }.getOrDefault("")
            val tokens = LabelReferenceParser.extractReferenceCandidates(text).take(MAX_LABEL_SEARCH_TOKENS)
            if (tokens.isEmpty()) return emptyList()
            return coroutineScope {
                tokens
                    .map { token -> async { runCatching { productsRepository.searchProducts(token) }.getOrDefault(emptyList()) } }
                    .awaitAll()
                    .flatten()
                    .distinctBy { it.id }
                    .take(MAX_LABEL_SUGGESTIONS)
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
         * Bascule directement sur la fiche d'ajustement, comme un scan trouvé — émet aussi
         * [scanFeedbackEvents] : le produit vient d'être affiché suite à un scan (Lot 3).
         */
        fun onProductResolvedExternally(product: Product) {
            _scanFeedbackEvents.tryEmit(Unit)
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
            _uiState.update {
                StockReplenishUiState(
                    pendingWrites = it.pendingWrites,
                    sessionRecap = it.sessionRecap,
                    queueAddedTick = it.queueAddedTick,
                )
            }
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

            // Compteur de session incrémenté de façon optimiste dès la validation (cf. KDoc classe) :
            // décrémenté si annulé pendant la fenêtre ou si l'écriture échoue en tâche de fond.
            _uiState.update {
                StockReplenishUiState(
                    pendingWrites = it.pendingWrites + pending,
                    sessionRecap = it.sessionRecap.plus(pending),
                    queueAddedTick = it.queueAddedTick + 1,
                )
            }

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
                                // L'écriture n'a réellement PAS eu lieu : retiré du récap de session.
                                sessionRecap = it.sessionRecap.minus(pending),
                            )
                        }
                    }
                }
        }

        /** Annule une écriture en attente (fenêtre des [REPLENISH_UNDO_DELAY_MS] ms) : aucun appel API. */
        fun onCancelPendingWrite(id: String) {
            pendingJobs.remove(id)?.cancel()
            _uiState.update { current ->
                val cancelled = current.pendingWrites.firstOrNull { it.id == id }
                current.copy(
                    pendingWrites = current.pendingWrites.filterNot { w -> w.id == id },
                    sessionRecap = cancelled?.let { current.sessionRecap.minus(it) } ?: current.sessionRecap,
                )
            }
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

/**
 * Récap courant de la session de réappro (Lot 3) : nombre d'articles et somme des unités
 * *réellement* validées (cf. KDoc [StockReplenishViewModel]) — affiché en entête d'écran, et en
 * synthèse à la sortie si [articleCount] > 0.
 */
data class ReplenishSessionRecap(
    val articleCount: Int = 0,
    val unitsCount: Int = 0,
) {
    operator fun plus(write: PendingStockWrite) = copy(articleCount = articleCount + 1, unitsCount = unitsCount + write.delta)

    operator fun minus(write: PendingStockWrite) = copy(articleCount = articleCount - 1, unitsCount = unitsCount - write.delta)
}

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
    /**
     * Secours OCR en cours (cf. KDoc [StockReplenishViewModel], section OCR) : tentative brève,
     * best-effort, entre l'échec du scan et la bascule sur `notFound`. Jamais vrai en même temps
     * que [notFound] (l'un succède strictement à l'autre).
     */
    val isLabelSearchLoading: Boolean = false,
    /**
     * Produits suggérés par le secours OCR (cf. KDoc [StockReplenishViewModel]) — vide si l'OCR
     * n'a rien trouvé (fallback silencieux sur l'association manuelle à vide) ou n'a pas été tenté.
     * Consommé côté écran pour pré-remplir [ProductScanViewModel.onKnownNotFoundWithSuggestions].
     */
    val labelSuggestions: List<Product> = emptyList(),
    /** Delta accumulé (boutons rapides + saisie libre), pas encore écrit. */
    val delta: Int = 0,
    val quantityInput: String = "",
    val error: UiText? = null,
    /** Écritures validées, en attente d'envoi (fenêtre d'annulation), potentiellement plusieurs en parallèle. */
    val pendingWrites: List<PendingStockWrite> = emptyList(),
    /** Message d'échec d'une écriture en tâche de fond (fenêtre d'annulation écoulée), à consommer. */
    val writeErrorMessage: String? = null,
    /** Récap courant de la session (Lot 3) — cf. [ReplenishSessionRecap]. */
    val sessionRecap: ReplenishSessionRecap = ReplenishSessionRecap(),
    /** Incrémenté à chaque [StockReplenishViewModel.onValidate] réussi (Lot 3, confirmation visuelle). */
    val queueAddedTick: Int = 0,
) {
    /** Le scanner permanent doit être actif (caméra allumée) seulement dans cet état. */
    val isScannerActive: Boolean
        get() =
            !isLookupLoading && !isLabelSearchLoading && product == null && combinationChoices.isEmpty() &&
                multipleResults.isEmpty() && !notFound

    /** Stock résultant si le delta accumulé était validé maintenant. */
    val newQuantity: Int get() = (product?.scannedQuantity ?: 0) + delta

    val canValidate: Boolean get() = product != null && delta != 0
}
