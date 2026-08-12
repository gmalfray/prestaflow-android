package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.util.TimeProvider
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.language.LanguageRepository
import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Taille de page par défaut pour la pagination. */
private const val PAGE_SIZE = OrdersRepository.DEFAULT_PAGE_SIZE

/** Debounce (ms) avant de déclencher une recherche serveur sur changement de query. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Délai minimal (ms) entre deux rafraîchissements automatiques déclenchés par le retour à l'écran
 * (cf. [OrdersViewModel.onScreenResumed]). 1 minute : assez court pour que les commandes reçues
 * pendant qu'on était sur un autre onglet apparaissent vite en revenant, assez long pour qu'un
 * aller-retour rapide entre deux onglets (quelques secondes, geste courant en navigation par
 * onglets) ne déclenche pas un appel réseau à chaque fois. Le tirer-pour-rafraîchir manuel
 * ([OrdersViewModel.onRefresh]) n'est jamais soumis à ce délai : c'est un geste explicite.
 */
internal const val AUTO_REFRESH_MIN_INTERVAL_MS = 60_000L

/**
 * IDs de statut PrestaShop pré-sélectionnés par défaut à l'ouverture de l'écran (« commandes à
 * traiter »). **Par ID (stable, indépendant de la langue)** et non par nom : le matching par nom FR
 * cassait dès que l'app affichait les statuts dans une autre langue (filtre par défaut vide) et
 * pouvait matcher des statuts non voulus (ex. « expedi » matchait aussi « En cours d'expédition »).
 * 2 = Paiement accepté, 3 = En cours de préparation, 4 = Expédié (IDs standards PrestaShop),
 * 9 = Terminée (pensebonheur). Un ID absent de la boutique est ignoré (intersection avec les
 * statuts réellement disponibles), donc sans effet sur une autre boutique.
 */
private val DEFAULT_STATUS_IDS = listOf(2, 3, 4, 9)

/**
 * IDs de statuts affichés par défaut comme chips-raccourcis dans la barre (quand aucune préférence
 * n'est enregistrée), au plus [MAX_VISIBLE_STATUS_CHIPS] : 3 = En préparation, 4 = Expédié,
 * 9 = Terminée. Par ID pour la même raison que [DEFAULT_STATUS_IDS].
 */
private val DEFAULT_VISIBLE_CHIP_IDS = listOf(3, 4, 9)

/** Nombre maximum de chips de statut dans la barre de filtres. */
internal const val MAX_VISIBLE_STATUS_CHIPS = 3

/**
 * Résout les IDs de statuts pré-sélectionnés par défaut, par intersection de [DEFAULT_STATUS_IDS]
 * avec les statuts réellement disponibles dans la boutique. Ensemble vide si aucun (fallback = tous).
 */
internal fun resolveDefaultStatusIds(availableStatuses: List<OrderStatusFilter>): Set<Int> {
    val availableIds = availableStatuses.mapTo(HashSet()) { it.id }
    return DEFAULT_STATUS_IDS.filter { it in availableIds }.toSet()
}

/**
 * Résout la liste de statuts à afficher par défaut dans la barre de chips.
 *
 * Stratégie :
 * - Prend les statuts dont l'ID figure dans [DEFAULT_VISIBLE_CHIP_IDS] (dans cet ordre), au plus
 *   [MAX_VISIBLE_STATUS_CHIPS].
 * - Si aucun de ces IDs n'existe dans la boutique, repli sur les [MAX_VISIBLE_STATUS_CHIPS] premiers
 *   statuts disponibles.
 */
internal fun resolveDefaultVisibleChips(availableStatuses: List<OrderStatusFilter>): List<OrderStatusFilter> {
    val byId = availableStatuses.associateBy { it.id }
    val matched = DEFAULT_VISIBLE_CHIP_IDS.mapNotNull { byId[it] }.take(MAX_VISIBLE_STATUS_CHIPS)
    return matched.ifEmpty { availableStatuses.take(MAX_VISIBLE_STATUS_CHIPS) }
}

/** Ordre de tri exposé à l'API (`sort` param). */
enum class OrderSort(val queryValue: String) {
    DATE_DESC("date_desc"),
    DATE_ASC("date_asc"),
    AMOUNT_DESC("total_desc"),
    AMOUNT_ASC("total_asc"),
    STATUS("status"),
    REFERENCE("reference"),
}

@OptIn(FlowPreview::class)
@HiltViewModel
@Suppress("LongParameterList") // Dépendances ViewModel (repos + mappers) toutes nécessaires, cf. SyncTaskExecutor
class OrdersViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ordersRepository: OrdersRepository,
        private val ordersPreferencesRepository: OrdersPreferencesRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
        private val languageRepository: LanguageRepository,
        private val timeProvider: TimeProvider,
    ) : ViewModel() {
        /**
         * Horodatage ([TimeProvider.nowMillis]) du dernier [refresh] réussi, `null` tant qu'aucun
         * n'a encore abouti. Sert uniquement de repère pour le throttle de [onScreenResumed] — n'est
         * volontairement pas exposé dans [OrdersUiState] (bookkeeping interne, pas un état d'écran).
         */
        private var lastSuccessfulRefreshAtMs: Long? = null

        private val _uiState =
            MutableStateFlow(
                OrdersUiState(
                    activePeriod =
                        savedStateHandle.get<String?>("period")
                            ?.let { periodValue -> DashboardPeriod.entries.find { it.queryValue == periodValue } },
                ),
            )
        val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

        init {
            observeOrders()
            observeVisibleStatusIds()
            initializeData()
            observeActiveShopSwitch()
            observeLanguageChange()
            observeSearchQuery()
        }

        /**
         * Observe la query de recherche (débounce 300 ms) pour déclencher une recherche CÔTÉ SERVEUR :
         * l'API cherche dans toute la base (référence + nom client), pas seulement les commandes déjà
         * chargées. Le filtre local [OrdersUiState.visibleOrders] ne reste qu'un repli d'affichage
         * (réseau KO → on garde au moins le cache filtré, pas d'écran vide).
         */
        private fun observeSearchQuery() {
            viewModelScope.launch {
                _uiState
                    .map { it.query }
                    .distinctUntilChanged()
                    .drop(1) // ignore la valeur initiale vide
                    .debounce(SEARCH_DEBOUNCE_MS)
                    .collect {
                        refresh(forceRemote = true, notifyOnError = false)
                    }
            }
        }

        /**
         * Charge les statuts disponibles PUIS déclenche le premier refresh avec les filtres par défaut.
         * Séquential pour que les filtres par défaut soient connus avant la requête orders.
         */
        private fun initializeData() {
            viewModelScope.launch {
                val statuses =
                    runCatching { ordersRepository.getOrderStatuses() }
                        .getOrElse { error ->
                            Timber.w(error, "Impossible de charger les statuts de commande")
                            emptyList()
                        }

                val defaultIds = if (statuses.isNotEmpty()) resolveDefaultStatusIds(statuses) else emptySet()
                _uiState.update { it.copy(availableStatuses = statuses, selectedStatusIds = defaultIds) }

                refresh(forceRemote = true, notifyOnError = false)
            }
        }

        fun onRefresh() {
            refresh(forceRemote = true, notifyOnError = true)
        }

        /**
         * Appelée quand l'écran Commandes redevient visible au premier plan (retour d'un autre
         * onglet, cf. [com.rebuildit.prestaflow.ui.orders.OrdersRoute]). Ce ViewModel est rattaché à
         * l'entrée de pile Navigation Compose et survit donc aux changements d'onglet — sans ce
         * rattrapage, `init` ne rejoue jamais et l'écran reste figé sur le cache Room jusqu'au
         * prochain tirer-pour-rafraîchir manuel.
         *
         * Rafraîchit seulement si :
         * - aucun chargement n'est déjà en cours ([OrdersUiState.isRefreshing]) — jamais deux
         *   chargements simultanés ;
         * - le dernier chargement réussi remonte à plus de [AUTO_REFRESH_MIN_INTERVAL_MS] — un
         *   aller-retour rapide entre deux onglets ne déclenche pas un appel réseau à chaque fois.
         *
         * Contrairement à [onRefresh] (geste manuel explicite, toujours immédiat, jamais throttlé),
         * c'est un rattrapage silencieux : `notifyOnError = false` pour ne jamais afficher d'erreur
         * si le réseau est indisponible au moment du retour — l'écran garde alors simplement le
         * cache existant, comme avant ce correctif. Réutilise les filtres/tri/période/recherche déjà
         * en place ([refresh] les relit depuis [_uiState] courant), donc ne perd ni ne réinitialise
         * rien de ce que l'utilisateur a déjà posé.
         */
        fun onScreenResumed() {
            val current = _uiState.value
            if (current.isRefreshing) return
            val lastRefresh = lastSuccessfulRefreshAtMs
            if (lastRefresh != null && timeProvider.nowMillis() - lastRefresh < AUTO_REFRESH_MIN_INTERVAL_MS) return
            refresh(forceRemote = true, notifyOnError = false)
        }

        /**
         * Efface le filtre de période hérité du dashboard et recharge la liste complète.
         * Les filtres de statut et le tri sont conservés.
         */
        fun clearPeriodFilter() {
            _uiState.update { it.copy(activePeriod = null) }
            refresh(forceRemote = true, notifyOnError = true)
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        // ─── Filtre multi-statuts ─────────────────────────────────────────────

        /**
         * Sélectionne [statusId] en **exclusif** (raccourci = isoler ce statut) puis recharge la liste.
         *
         * Taper un chip affiche SEULEMENT ce statut — y compris s'il faisait déjà partie d'un filtre
         * multi-statuts (ex. le défaut « à traiter » 2/3/4/9, cf. [DEFAULT_STATUS_IDS]) : le mental
         * model d'un chip « raccourci » est « montre-moi CE statut », pas « ajoute/retire ce statut
         * d'un ensemble ». Re-tap sur l'UNIQUE statut déjà sélectionné → désélectionne (retour à
         * l'état par défaut, rien de sélectionné = toutes les commandes affichées ; pas de chip
         * « Toutes » dédié dans la barre, ce re-tap en est le seul chemin). [statusId] `null`
         * réinitialise le filtre de la même façon (utilisé par l'appel programmatique, ex. bouton
         * de réinitialisation affiché quand un filtre actif ne donne aucun résultat).
         *
         * RÉGRESSION v0.41.2 (fixée ici) : le tap avait été rendu « indépendamment toggleable »
         * (ajoute/retire du filtre courant, commit 0f932c6) pour permettre de retirer un statut du
         * filtre par défaut multi-statuts. Mais comme ce défaut sélectionne déjà plusieurs statuts
         * SANS que l'utilisateur les ait tapés, taper un chip qui en fait déjà partie (ex. « Prépa »)
         * le RETIRAIT (exclude) au lieu d'isoler dessus (include) : sélectionner un statut faisait
         * disparaître exactement les commandes de ce statut, l'inverse de l'effet attendu. Le vrai
         * multi-statuts reste possible via le menu « Filtrer par statut » ([onStatusFiltersReplaced]),
         * qui pose l'ensemble complet en une fois (checkboxes dédiées, mécanisme séparé des chips).
         */
        fun onStatusFilterSelected(statusId: Int?) {
            _uiState.update { current ->
                val newSelection =
                    when {
                        // "Toutes" → aucun filtre.
                        statusId == null -> emptySet()
                        // Re-tap sur l'unique statut déjà sélectionné → désélectionne (retour à "Toutes").
                        current.selectedStatusIds == setOf(statusId) -> emptySet()
                        // Sinon → sélection EXCLUSIVE : n'affiche QUE ce statut.
                        else -> setOf(statusId)
                    }
                current.copy(selectedStatusIds = newSelection)
            }
            refresh(forceRemote = true, notifyOnError = true)
        }

        // ─── Tri ─────────────────────────────────────────────────────────────

        /** Change l'ordre de tri et recharge depuis la première page. */
        fun onSortChanged(sort: OrderSort) {
            _uiState.update { it.copy(selectedSort = sort, hasMore = false) }
            refresh(forceRemote = true, notifyOnError = true)
        }

        // ─── Pagination ──────────────────────────────────────────────────────

        /**
         * Charge la page suivante de commandes (offset = nombre de commandes déjà chargées).
         * Ne fait rien si un chargement est déjà en cours ou s'il n'y a plus de page.
         */
        fun loadMore() {
            val current = _uiState.value
            if (current.isLoadingMore || !current.hasMore) return
            val nextOffset = current.orders.size
            _uiState.update { it.copy(isLoadingMore = true) }
            viewModelScope.launch {
                val (dateFrom, dateTo) = current.activePeriod?.toDateRange() ?: Pair(null, null)
                val hasMore =
                    runCatching {
                        ordersRepository.refresh(
                            forceRemote = true,
                            statusIds = current.selectedStatusIds,
                            sort = current.selectedSort.queryValue,
                            dateFrom = dateFrom,
                            dateTo = dateTo,
                            offset = nextOffset,
                            limit = PAGE_SIZE,
                            search = current.query.takeIf { it.isNotBlank() },
                        )
                    }.getOrElse { error ->
                        Timber.w(error, "Échec loadMore commandes offset=$nextOffset")
                        _uiState.update {
                            it.copy(
                                isLoadingMore = false,
                                error = networkErrorMapper.map(error),
                            )
                        }
                        return@launch
                    }
                _uiState.update { it.copy(isLoadingMore = false, hasMore = hasMore) }
            }
        }

        // ─── Préférence de statuts visibles ──────────────────────────────────

        /**
         * Observe la préférence DataStore et met à jour l'état.
         *
         * [visibleStatusIds] ne pilote QUE les chips raccourcis affichés dans la barre : le filtre
         * actif ([OrdersUiState.selectedStatusIds]) reste indépendant et peut porter sur n'importe
         * quel statut disponible, y compris un statut non épinglé en raccourci.
         */
        private fun observeVisibleStatusIds() {
            viewModelScope.launch {
                ordersPreferencesRepository.visibleStatusIds.collect { ids ->
                    _uiState.update { current -> current.copy(visibleStatusIds = ids) }
                }
            }
        }

        /**
         * Persiste les IDs de statuts à afficher dans la barre de filtres (raccourcis, max
         * [MAX_VISIBLE_STATUS_CHIPS]). Si [ids] est vide, réinitialise la préférence (retour au
         * défaut curaté). N'affecte jamais le filtre actif ([selectedStatusIds]).
         */
        fun onVisibleStatusIdsChanged(ids: Set<Int>) {
            viewModelScope.launch {
                if (ids.isEmpty()) {
                    ordersPreferencesRepository.clearVisibleStatusIds()
                } else {
                    ordersPreferencesRepository.setVisibleStatusIds(ids)
                }
            }
        }

        /**
         * Remplace intégralement le filtre de statuts actif par [ids] puis recharge la liste.
         * Contrairement à [onStatusFilterSelected] (bascule un seul statut, utilisé par les chips
         * de la barre), cette fonction pose l'ensemble en une fois — utilisée par le volet
         * « Filtrer par statut » du menu, qui autorise 100 % des statuts disponibles (pas de
         * plafond, pas d'intersection avec les raccourcis).
         */
        fun onStatusFiltersReplaced(ids: Set<Int>) {
            _uiState.update { it.copy(selectedStatusIds = ids) }
            refresh(forceRemote = true, notifyOnError = true)
        }

        // ─── Sélection multiple ──────────────────────────────────────────────

        /** Active le mode sélection et sélectionne la commande [orderId] (appui long). */
        fun onOrderLongPress(orderId: Long) {
            _uiState.update { current ->
                val order = current.orders.find { it.id == orderId }
                // Les commandes sans facture ne sont pas sélectionnables
                if (order == null || !order.hasInvoice) return@update current
                current.copy(
                    selectionMode = true,
                    selectedOrderIds = current.selectedOrderIds + orderId,
                )
            }
        }

        /** Bascule la sélection d'une commande (en mode sélection actif). */
        fun onOrderSelectionToggle(orderId: Long) {
            _uiState.update { current ->
                if (!current.selectionMode) return@update current
                val order = current.orders.find { it.id == orderId }
                if (order == null || !order.hasInvoice) return@update current
                val newSelection =
                    if (orderId in current.selectedOrderIds) {
                        current.selectedOrderIds - orderId
                    } else {
                        current.selectedOrderIds + orderId
                    }
                current.copy(
                    selectionMode = newSelection.isNotEmpty(),
                    selectedOrderIds = newSelection,
                )
            }
        }

        /** Quitte le mode sélection sans déclencher d'impression. */
        fun cancelSelection() {
            _uiState.update { it.copy(selectionMode = false, selectedOrderIds = emptySet()) }
        }

        /**
         * Change le statut de toutes les commandes sélectionnées vers [statusId].
         */
        fun bulkUpdateStatus(statusId: String) {
            val selectedIds = _uiState.value.selectedOrderIds.toList()
            if (selectedIds.isEmpty()) return
            viewModelScope.launch {
                _uiState.update { it.copy(isBulkUpdating = true) }
                var successCount = 0
                var failureCount = 0
                selectedIds.forEach { orderId ->
                    runCatching {
                        ordersRepository.updateOrderStatus(orderId, statusId)
                    }.onSuccess {
                        successCount++
                    }.onFailure { error ->
                        failureCount++
                        Timber.w(error, "Échec mise à jour statut commande #%d", orderId)
                    }
                }
                _uiState.update { current ->
                    val message =
                        if (failureCount == 0) {
                            "$successCount commande(s) mise(s) à jour"
                        } else {
                            "$successCount mise(s) à jour, $failureCount échec(s)"
                        }
                    current.copy(
                        isBulkUpdating = false,
                        selectionMode = false,
                        selectedOrderIds = emptySet(),
                        bulkSnackbar = message,
                    )
                }
                refresh(forceRemote = true, notifyOnError = false)
            }
        }

        /** Consomme le message snackbar de mise à jour en lot. */
        fun consumeBulkSnackbar() {
            _uiState.update { it.copy(bulkSnackbar = null) }
        }

        /**
         * Télécharge les PDFs des commandes sélectionnées et invoque [onReady] avec les octets.
         */
        fun printSelectedInvoices(onReady: (List<ByteArray>) -> Unit) {
            val selectedIds = _uiState.value.selectedOrderIds.toList()
            if (selectedIds.isEmpty()) return
            viewModelScope.launch {
                _uiState.update { it.copy(isPrintingInProgress = true) }
                runCatching {
                    selectedIds.mapNotNull { id -> ordersRepository.downloadInvoicePdf(id) }
                }.onSuccess { pdfList ->
                    _uiState.update { it.copy(isPrintingInProgress = false, selectionMode = false, selectedOrderIds = emptySet()) }
                    if (pdfList.isNotEmpty()) onReady(pdfList)
                }.onFailure { error ->
                    Timber.w(error, "Échec du téléchargement des factures sélectionnées")
                    _uiState.update { it.copy(isPrintingInProgress = false, printError = error.message ?: "Erreur d'impression") }
                }
            }
        }

        /** Consomme le message d'erreur d'impression. */
        fun consumePrintError() {
            _uiState.update { it.copy(printError = null) }
        }

        // ─── Rafraîchissement ─────────────────────────────────────────────────

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(
                                orders = emptyList(),
                                isLoading = true,
                                error = null,
                                selectionMode = false,
                                selectedOrderIds = emptySet(),
                                selectedStatusIds = emptySet(),
                                availableStatuses = emptyList(),
                                hasMore = false,
                            )
                        }
                        initializeData()
                    }
            }
        }

        /**
         * Recharge la liste des commandes quand la langue d'affichage change (bascule in-app ou
         * retour au mode « Système »).
         *
         * Le contenu localisé côté serveur (statuts de commande notamment) est renvoyé selon le
         * header `Accept-Language` posé par [com.rebuildit.prestaflow.data.remote.interceptor.AcceptLanguageInterceptor],
         * lui-même dérivé de cette même langue d'affichage. Un changement de langue déclenche
         * normalement une recréation d'Activity — mais ce ViewModel (scope Navigation Compose)
         * SURVIT à cette recréation, donc `init` ne se relance pas et les commandes déjà en cache
         * Room resteraient affichées dans l'ancienne langue sans ce refresh explicite.
         */
        private fun observeLanguageChange() {
            viewModelScope.launch {
                languageRepository.currentLanguageTag
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        // Recharge AUSSI la liste des statuts : leurs libellés (badges de commande ET
                        // puces de filtre de statut) sont localisés côté serveur selon Accept-Language.
                        // Sans ce rechargement, seules les commandes se rafraîchissaient et les puces
                        // restaient dans l'ancienne langue (ex. « Expédié » en app allemande). Les IDs
                        // de statut sont stables entre langues → on met à jour les noms sans toucher à
                        // la sélection courante (selectedStatusIds).
                        runCatching { ordersRepository.getOrderStatuses() }
                            .onSuccess { statuses ->
                                if (statuses.isNotEmpty()) {
                                    _uiState.update { it.copy(availableStatuses = statuses) }
                                }
                            }
                            .onFailure { error ->
                                Timber.w(error, "Rechargement des statuts au changement de langue échoué")
                            }
                        refresh(forceRemote = true, notifyOnError = false)
                    }
            }
        }

        private fun observeOrders() {
            viewModelScope.launch {
                ordersRepository.observeOrders().collect { orders ->
                    _uiState.update { current ->
                        current.copy(
                            orders = orders,
                            // Ne quitte l'état de chargement que si des commandes sont arrivées, OU
                            // si le refresh réseau initial a déjà tranché (current.isLoading déjà à
                            // false via refresh()). Sinon, la 1ère émission Room (cache vide au 1er
                            // lancement ou après changement de boutique) ferait flasher l'état
                            // "vide" avant que la réponse réseau n'arrive — cause du flash de page
                            // blanche/vide signalé en navigation.
                            isLoading = current.isLoading && orders.isEmpty(),
                            isRefreshing = false,
                            error = if (orders.isNotEmpty()) null else current.error,
                        )
                    }
                }
            }
        }

        fun refresh(
            forceRemote: Boolean,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.orders.isEmpty(),
                        hasMore = false,
                        error = if (notifyOnError) null else current.error,
                    )
                }

                val current = _uiState.value
                val (dateFrom, dateTo) = current.activePeriod?.toDateRange() ?: Pair(null, null)
                runCatching {
                    ordersRepository.refresh(
                        forceRemote = forceRemote,
                        statusIds = current.selectedStatusIds,
                        sort = current.selectedSort.queryValue,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        offset = 0,
                        limit = PAGE_SIZE,
                        search = current.query.takeIf { it.isNotBlank() },
                    )
                }.onFailure { error ->
                    Timber.w(error, "Failed to refresh orders")
                    _uiState.update { state ->
                        val mapped = networkErrorMapper.map(error)
                        state.copy(
                            isRefreshing = false,
                            isLoading = false,
                            error = if (notifyOnError) mapped else state.error,
                            // Recherche serveur en échec avec une query active → on autorise le repli
                            // local (filtrage du cache) pour ne pas afficher un écran vide hors-ligne.
                            searchFallback = state.query.isNotBlank(),
                        )
                    }
                }.onSuccess { hasMore ->
                    _uiState.update { state ->
                        state.copy(
                            isRefreshing = false,
                            isLoading = false,
                            error = null,
                            hasMore = hasMore,
                            // La recherche serveur a réussi → on affiche ses résultats tels quels
                            // (pas de re-filtrage local, qui masquerait les matchs email/nom complet).
                            searchFallback = false,
                        )
                    }
                    // Repère du throttle de onScreenResumed — un échec ne l'avance JAMAIS (onFailure
                    // ci-dessus ne touche pas à ce champ), pour ne pas geler le rattrapage automatique
                    // sur un timestamp d'échec.
                    lastSuccessfulRefreshAtMs = timeProvider.nowMillis()
                    markCurrentListSeen()
                }
            }
        }

        /**
         * Avance le repère "dernière commande vue" de la boutique active jusqu'au plus haut ID de
         * la liste tout juste chargée — appelé UNIQUEMENT après un [refresh] réussi ([onFailure]
         * n'appelle jamais cette fonction : un écran en erreur ou vide ne doit jamais faire
         * disparaître la pastille sans que rien n'ait été vu).
         *
         * Lit [OrdersRepository.observeOrders] (cache Room déjà à jour à ce point, `refresh` a
         * suspendu jusqu'à l'upsert) plutôt que `_uiState.value.orders`, qui peut ne pas encore
         * refléter le résultat de CE refresh précis (le collecteur [observeOrders] tourne dans sa
         * propre coroutine) — aucun appel réseau supplémentaire, uniquement une lecture locale.
         */
        private suspend fun markCurrentListSeen() {
            val shopId = authRepository.connections.value.firstOrNull { it.isActive }?.id ?: return
            val maxOrderId = ordersRepository.observeOrders().first().maxOfOrNull { it.id } ?: return
            ordersPreferencesRepository.markOrdersListSeen(shopId, maxOrderId)
        }
    }

/** Nombre de jours en arrière (inclus) pour couvrir une semaine glissante de 7 jours. */
private const val WEEK_RANGE_DAYS_BACK = 6L

/** Nombre de jours en arrière (inclus) pour couvrir un mois glissant de 30 jours. */
private const val MONTH_RANGE_DAYS_BACK = 29L

/**
 * Convertit une [DashboardPeriod] en plage (dateFrom, dateTo) pour le filtre `GET /orders`.
 */
internal fun DashboardPeriod.toDateRange(today: LocalDate = LocalDate.now()): Pair<String, String> {
    val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    val fromDate =
        when (this) {
            DashboardPeriod.TODAY -> today
            DashboardPeriod.WEEK -> today.minusDays(WEEK_RANGE_DAYS_BACK)
            DashboardPeriod.MONTH -> today.minusDays(MONTH_RANGE_DAYS_BACK)
            DashboardPeriod.QUARTER -> today.minusMonths(3)
            DashboardPeriod.YEAR -> today.withDayOfYear(1)
        }
    return Pair(fromDate.format(dateFmt), "${today.format(dateFmt)} 23:59:59")
}

data class OrdersUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    /**
     * Vrai quand la dernière recherche serveur a échoué alors qu'une query est active : autorise le
     * filtrage local du cache en repli ([visibleOrders]). Faux quand la recherche serveur a réussi
     * (on affiche alors les résultats de l'API tels quels, sans re-filtrage local).
     */
    val searchFallback: Boolean = false,
    /**
     * Période dashboard active (héritée du nav arg "period").
     * Null = aucun filtre de période actif (liste complète, accès direct via navigation).
     */
    val activePeriod: DashboardPeriod? = null,
    /** Mode sélection multiple actif (déclenché par appui long). */
    val selectionMode: Boolean = false,
    /** IDs des commandes sélectionnées (toutes avec has_invoice=true). */
    val selectedOrderIds: Set<Long> = emptySet(),
    /** Vrai pendant le téléchargement des PDFs pour impression. */
    val isPrintingInProgress: Boolean = false,
    /** Vrai pendant la mise à jour de statut en lot. */
    val isBulkUpdating: Boolean = false,
    /** Message d'erreur d'impression à afficher puis consommer. */
    val printError: String? = null,
    /** Message snackbar résumant le résultat de la mise à jour en lot. */
    val bulkSnackbar: String? = null,
    /** Statuts disponibles pour le filtre, chargés depuis l'API. */
    val availableStatuses: List<OrderStatusFilter> = emptyList(),
    /**
     * IDs des statuts actuellement actifs dans le filtre.
     * Ensemble vide = toutes les commandes (aucun filtre appliqué).
     */
    val selectedStatusIds: Set<Int> = emptySet(),
    /**
     * IDs des statuts à afficher dans la barre de filtres (préférence persistée).
     * Null = aucune préférence → tous les [availableStatuses] sont affichés.
     */
    val visibleStatusIds: Set<Int>? = null,
    /** Ordre de tri courant. */
    val selectedSort: OrderSort = OrderSort.DATE_DESC,
    /** Vrai si d'autres commandes sont disponibles au-delà de celles déjà chargées. */
    val hasMore: Boolean = false,
    /** Vrai pendant le chargement d'une page supplémentaire (pagination). */
    val isLoadingMore: Boolean = false,
) {
    /**
     * Statuts effectivement affichés dans la barre de filtres.
     * Si [visibleStatusIds] est null (aucune préférence enregistrée), retourne le défaut curaté
     * (jusqu'à [MAX_VISIBLE_STATUS_CHIPS] statuts résolus par nom via [resolveDefaultVisibleChips]).
     */
    val filteredStatuses: List<OrderStatusFilter>
        get() =
            visibleStatusIds?.let { ids ->
                availableStatuses.filter { it.id in ids }
            } ?: resolveDefaultVisibleChips(availableStatuses)

    /**
     * Liste affichée : filtrée par [query] sur le nom du client et la référence (insensible à la casse).
     *
     * Le filtre par STATUT est appliqué **côté serveur** (param `statuses=`) : `refresh` vide Room puis
     * insère uniquement les commandes filtrées. On ne re-filtre donc PAS par statut ici — le connecteur
     * ne renvoie pas `current_state_id` dans la liste (toujours 0), un filtre client sur ce champ
     * masquerait tout.
     */
    val visibleOrders: List<Order>
        get() =
            when {
                query.isBlank() -> orders
                // La recherche est déléguée au SERVEUR (référence + nom/prénom + email) : `orders`
                // contient déjà exactement les résultats filtrés par l'API. On ne re-filtre PAS en
                // local (le serveur matche l'email et le nom complet, que le filtre local ignore).
                !searchFallback -> orders
                // Repli hors-ligne uniquement (recherche serveur en échec) : on filtre le cache par
                // nom/référence pour au moins montrer quelque chose.
                else ->
                    orders.filter {
                        it.customerName.contains(query, ignoreCase = true) ||
                            it.reference.contains(query, ignoreCase = true)
                    }
            }

    /** Vrai si au moins un filtre de statut est actif. */
    val hasActiveStatusFilter: Boolean get() = selectedStatusIds.isNotEmpty()
}
