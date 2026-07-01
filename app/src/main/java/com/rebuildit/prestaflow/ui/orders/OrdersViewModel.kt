package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.Order
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Durée (ms) avant envoi effectif du changement de statut après un swipe. */
private const val SWIPE_UNDO_DELAY_MS = 5_000L

/** Taille de page par défaut pour la pagination. */
private const val PAGE_SIZE = OrdersRepository.DEFAULT_PAGE_SIZE

/**
 * Noms de statuts PrestaShop à activer par défaut lors de l'ouverture de l'écran.
 * Chaque entrée est comparée après normalisation (minuscules, sans accents, sans espaces superflus)
 * au nom des statuts disponibles (correspondance par sous-chaîne).
 */
private val DEFAULT_STATUS_MATCHERS = listOf(
    "paiement accepte",
    "preparation",
    "expedi",
    "termin",
)

/**
 * Normalise une chaîne pour la comparaison insensible à la casse et aux accents.
 * `"Paiement accepté"` → `"paiement accepte"`.
 */
internal fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
        .trim()

/**
 * Résout les IDs de statuts correspondant aux noms par défaut dans [availableStatuses].
 * Retourne un ensemble vide si aucun statut ne correspond (fallback = tous).
 */
internal fun resolveDefaultStatusIds(availableStatuses: List<OrderStatusFilter>): Set<Int> =
    availableStatuses
        .filter { status ->
            val n = status.name.normalizeForMatch()
            DEFAULT_STATUS_MATCHERS.any { matcher -> n.contains(matcher) }
        }
        .map { it.id }
        .toSet()

/** Sens du swipe sur une ligne de commande. */
enum class SwipeDirection { LEFT, RIGHT }

/** Ordre de tri exposé à l'API (`sort` param). */
enum class OrderSort(val queryValue: String) {
    DATE_DESC("date_desc"),
    DATE_ASC("date_asc"),
    AMOUNT_DESC("total_desc"),
    AMOUNT_ASC("total_asc"),
    STATUS("status"),
    REFERENCE("reference"),
}

/**
 * Action de changement de statut en attente d'exécution (délai d'annulation 5 s).
 */
data class PendingSwipeAction(
    val orderId: Long,
    val orderReference: String,
    val targetStatusId: Int,
    val targetStatusName: String,
)

@HiltViewModel
class OrdersViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ordersRepository: OrdersRepository,
        private val ordersPreferencesRepository: OrdersPreferencesRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(OrdersUiState())
        val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

        /**
         * Plage de dates dérivée de la période dashboard transmise via nav arg "period".
         * Null si l'écran est ouvert sans filtre de période (accès direct depuis la barre de navigation).
         */
        private val periodDateRange: Pair<String, String>? =
            savedStateHandle.get<String?>("period")
                ?.let { periodValue -> DashboardPeriod.entries.find { it.queryValue == periodValue } }
                ?.toDateRange()

        /** Job en cours pour le swipe avec délai d'annulation. */
        private var pendingSwipeJob: Job? = null

        init {
            observeOrders()
            observeVisibleStatusIds()
            initializeData()
            observeActiveShopSwitch()
        }

        /**
         * Charge les statuts disponibles PUIS déclenche le premier refresh avec les filtres par défaut.
         * Séquential pour que les filtres par défaut soient connus avant la requête orders.
         */
        private fun initializeData() {
            viewModelScope.launch {
                val statuses = runCatching { ordersRepository.getOrderStatuses() }
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

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        // ─── Filtre multi-statuts ─────────────────────────────────────────────

        /**
         * Bascule le statut [statusId] dans / hors du filtre actif puis recharge la liste.
         * Si [statusId] est null, réinitialise tous les filtres.
         */
        fun onStatusFilterSelected(statusId: Int?) {
            if (statusId == null) {
                _uiState.update { it.copy(selectedStatusIds = emptySet()) }
            } else {
                _uiState.update { current ->
                    val ids = current.selectedStatusIds
                    current.copy(
                        selectedStatusIds = if (statusId in ids) ids - statusId else ids + statusId,
                    )
                }
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
                val (dateFrom, dateTo) = periodDateRange ?: Pair(null, null)
                val hasMore = runCatching {
                    ordersRepository.refresh(
                        forceRemote = true,
                        statusIds = current.selectedStatusIds,
                        sort = current.selectedSort.queryValue,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        offset = nextOffset,
                        limit = PAGE_SIZE,
                    )
                }.getOrElse { error ->
                    Timber.w(error, "Échec loadMore commandes offset=$nextOffset")
                    _uiState.update { it.copy(
                        isLoadingMore = false,
                        error = networkErrorMapper.map(error),
                    )}
                    return@launch
                }
                _uiState.update { it.copy(isLoadingMore = false, hasMore = hasMore) }
            }
        }

        // ─── Swipe avec délai d'annulation ───────────────────────────────────

        /**
         * Déclenche un changement de statut via swipe sur une commande.
         *
         * - Swipe GAUCHE → "En cours de préparation" (matcher "preparation")
         * - Swipe DROITE → "Terminé" (matcher "termin") ou "Livré" (matcher "livr")
         *
         * La résolution se fait par **nom normalisé** (insensible casse/accents).
         * L'appel API n'est envoyé qu'après [SWIPE_UNDO_DELAY_MS] ms. Si un autre swipe
         * arrive avant, le précédent est annulé (sans envoi).
         */
        fun onSwipeAction(
            orderId: Long,
            orderReference: String,
            direction: SwipeDirection,
        ) {
            val statuses = _uiState.value.availableStatuses
            val targetStatus = when (direction) {
                SwipeDirection.LEFT ->
                    statuses.firstOrNull { it.name.normalizeForMatch().contains("preparation") }
                SwipeDirection.RIGHT ->
                    statuses.firstOrNull { s ->
                        val n = s.name.normalizeForMatch()
                        n.contains("termin") || n.contains("livr")
                    }
            } ?: run {
                Timber.d("Swipe ignoré : aucun statut cible trouvé pour direction=$direction")
                return
            }

            // Annule l'action précédente (sans appel API)
            pendingSwipeJob?.cancel()

            _uiState.update {
                it.copy(
                    pendingSwipeAction = PendingSwipeAction(
                        orderId = orderId,
                        orderReference = orderReference,
                        targetStatusId = targetStatus.id,
                        targetStatusName = targetStatus.name,
                    ),
                )
            }

            pendingSwipeJob = viewModelScope.launch {
                delay(SWIPE_UNDO_DELAY_MS)
                // Délai écoulé → envoyer le changement
                runCatching {
                    ordersRepository.updateOrderStatus(orderId, targetStatus.id.toString())
                }.onFailure { error ->
                    Timber.w(error, "Swipe status update failed orderId=$orderId")
                }
                _uiState.update { it.copy(pendingSwipeAction = null) }
                refresh(forceRemote = true, notifyOnError = false)
            }
        }

        /** Annule l'action de swipe en attente (sans envoi API). */
        fun cancelSwipeAction() {
            pendingSwipeJob?.cancel()
            pendingSwipeJob = null
            _uiState.update { it.copy(pendingSwipeAction = null) }
        }

        // ─── Préférence de statuts visibles ──────────────────────────────────

        /** Observe la préférence DataStore et met à jour l'état. */
        private fun observeVisibleStatusIds() {
            viewModelScope.launch {
                ordersPreferencesRepository.visibleStatusIds.collect { ids ->
                    _uiState.update { current ->
                        val newState = current.copy(visibleStatusIds = ids)
                        // Si un statut sélectionné n'est plus visible, le retirer du filtre
                        val validSelectedIds =
                            if (ids != null) {
                                current.selectedStatusIds.intersect(ids)
                            } else {
                                current.selectedStatusIds
                            }
                        newState.copy(selectedStatusIds = validSelectedIds)
                    }
                }
            }
        }

        /**
         * Persiste les IDs de statuts à afficher dans la barre de filtres.
         * Si [ids] est vide, réinitialise la préférence (tous les statuts affichés).
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

        private fun observeOrders() {
            viewModelScope.launch {
                ordersRepository.observeOrders().collect { orders ->
                    _uiState.update { current ->
                        current.copy(
                            orders = orders,
                            isLoading = false,
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
                val (dateFrom, dateTo) = periodDateRange ?: Pair(null, null)
                runCatching {
                    ordersRepository.refresh(
                        forceRemote = forceRemote,
                        statusIds = current.selectedStatusIds,
                        sort = current.selectedSort.queryValue,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        offset = 0,
                        limit = PAGE_SIZE,
                    )
                }.onFailure { error ->
                    Timber.w(error, "Failed to refresh orders")
                    _uiState.update { state ->
                        val mapped = networkErrorMapper.map(error)
                        state.copy(
                            isRefreshing = false,
                            isLoading = false,
                            error = if (notifyOnError) mapped else state.error,
                        )
                    }
                }.onSuccess { hasMore ->
                    _uiState.update { state ->
                        state.copy(
                            isRefreshing = false,
                            isLoading = false,
                            error = null,
                            hasMore = hasMore,
                        )
                    }
                }
            }
        }
    }

/**
 * Convertit une [DashboardPeriod] en plage (dateFrom, dateTo) pour le filtre `GET /orders`.
 */
internal fun DashboardPeriod.toDateRange(today: LocalDate = LocalDate.now()): Pair<String, String> {
    val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    val fromDate =
        when (this) {
            DashboardPeriod.TODAY -> today
            DashboardPeriod.WEEK -> today.minusDays(6)
            DashboardPeriod.MONTH -> today.minusDays(29)
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
    /** Action de swipe en attente (délai d'annulation). Null = aucune action en cours. */
    val pendingSwipeAction: PendingSwipeAction? = null,
) {
    /**
     * Statuts effectivement affichés dans la barre de filtres.
     * Si [visibleStatusIds] est null, tous les [availableStatuses] sont retournés.
     */
    val filteredStatuses: List<OrderStatusFilter>
        get() =
            visibleStatusIds?.let { ids ->
                availableStatuses.filter { it.id in ids }
            } ?: availableStatuses

    /** Liste filtrée par [query] sur le nom du client et la référence (insensible à la casse). */
    val visibleOrders: List<Order>
        get() =
            if (query.isBlank()) {
                orders
            } else {
                orders.filter {
                    it.customerName.contains(query, ignoreCase = true) ||
                        it.reference.contains(query, ignoreCase = true)
                }
            }

    /** Vrai si au moins un filtre de statut est actif. */
    val hasActiveStatusFilter: Boolean get() = selectedStatusIds.isNotEmpty()
}
