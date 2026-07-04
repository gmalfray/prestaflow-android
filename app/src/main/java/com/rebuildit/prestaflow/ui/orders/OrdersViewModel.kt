package com.rebuildit.prestaflow.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.language.LanguageRepository
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Durée (ms) de la fenêtre « Annuler » avant envoi effectif du changement de statut après un swipe.
 * Non-private : réutilisée par [OrdersScreen][com.rebuildit.prestaflow.ui.orders.OrdersScreen]
 * (côté UI) pour faire tourner le décompte visible en secondes, en cohérence avec ce délai réel.
 */
internal const val SWIPE_UNDO_DELAY_MS = 10_000L

/**
 * Configuration du swipe telle qu'exposée dans l'UiState.
 *
 * Les ids [sourceStatusId], [leftTargetStatusId] et [rightTargetStatusId] proviennent
 * des préférences utilisateur. Quand ils sont null, la résolution se fait par nom
 * (comportement historique). Cette résolution est effectuée dans [OrdersViewModel.onSwipeAction].
 */
data class SwipeConfig(
    val enabled: Boolean = true,
    val sourceStatusId: Int? = null,
    val leftTargetStatusId: Int? = null,
    val rightTargetStatusId: Int? = null,
)

/** Taille de page par défaut pour la pagination. */
private const val PAGE_SIZE = OrdersRepository.DEFAULT_PAGE_SIZE

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

/** Nombre maximum de chips de statut dans la barre de filtres (hors chip « Toutes »). */
internal const val MAX_VISIBLE_STATUS_CHIPS = 3

// IDs de statut PrestaShop utilisés comme défauts du swipe quand rien n'est configuré. Par ID
// (stable, indépendant de la langue) : le repli historique par nom FR cassait dès que les statuts
// étaient affichés dans une autre langue (contenu localisé serveur via Accept-Language).
internal const val SWIPE_DEFAULT_SOURCE_ID = 2 // Paiement accepté
private const val SWIPE_DEFAULT_LEFT_TARGET_ID = 3 // En cours de préparation
private const val SWIPE_DEFAULT_RIGHT_TARGET_ID = 9 // Terminée
private const val SWIPE_DEFAULT_RIGHT_FALLBACK_ID = 5 // Livré

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
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow(
                OrdersUiState(
                    activePeriod =
                        savedStateHandle.get<String?>("period")
                            ?.let { periodValue -> DashboardPeriod.entries.find { it.queryValue == periodValue } },
                ),
            )
        val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

        /** Job en cours pour le swipe avec délai d'annulation. */
        private var pendingSwipeJob: Job? = null

        init {
            observeOrders()
            observeVisibleStatusIds()
            observeSwipeConfig()
            initializeData()
            observeActiveShopSwitch()
            observeLanguageChange()
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
         * Bascule le statut [statusId] dans / hors du filtre actif puis recharge la liste.
         *
         * Chaque chip est **indépendamment toggleable** : taper un statut inactif l'ajoute au filtre,
         * taper un statut actif le retire — y compris les statuts sélectionnés par défaut. Le filtre
         * peut donc porter sur plusieurs statuts simultanément (cohérent avec l'affichage multi-chips
         * de la barre). [statusId] `null` (chip « Toutes ») réinitialise le filtre.
         */
        fun onStatusFilterSelected(statusId: Int?) {
            _uiState.update { current ->
                val newSelection =
                    when {
                        // "Toutes" → aucun filtre.
                        statusId == null -> emptySet()
                        // Chip déjà actif → on le retire (désélection indépendante, défaut compris).
                        statusId in current.selectedStatusIds -> current.selectedStatusIds - statusId
                        // Sinon → on l'ajoute à la sélection (multi-statuts).
                        else -> current.selectedStatusIds + statusId
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

        // ─── Config swipe ──────────────────────────────────────────────────────

        /** Observe les préférences de configuration du swipe et met à jour l'UiState. */
        private fun observeSwipeConfig() {
            viewModelScope.launch {
                kotlinx.coroutines.flow.combine(
                    ordersPreferencesRepository.swipeEnabled,
                    ordersPreferencesRepository.swipeSourceStatusId,
                    ordersPreferencesRepository.swipeLeftTargetStatusId,
                    ordersPreferencesRepository.swipeRightTargetStatusId,
                ) { enabled, sourceId, leftId, rightId ->
                    SwipeConfig(
                        enabled = enabled,
                        sourceStatusId = sourceId,
                        leftTargetStatusId = leftId,
                        rightTargetStatusId = rightId,
                    )
                }.collect { config ->
                    _uiState.update { it.copy(swipeConfig = config) }
                }
            }
        }

        // ─── Swipe avec délai d'annulation ───────────────────────────────────

        /**
         * Déclenche un changement de statut via swipe sur une commande.
         *
         * La résolution de la source/cibles se fait :
         * - **Par ID** si configuré dans les préférences swipe.
         * - **Par nom normalisé** (repli) si l'ID est null ou introuvable :
         *   - Source : matcher "paiement accepte"
         *   - Gauche  : matcher "preparation"
         *   - Droite  : matcher "termin" puis "livr"
         *
         * L'appel API n'est envoyé qu'après [SWIPE_UNDO_DELAY_MS] ms. Si un autre swipe
         * arrive avant, le précédent est annulé (sans envoi).
         */
        fun onSwipeAction(
            orderId: Long,
            orderReference: String,
            direction: SwipeDirection,
        ) {
            val config = _uiState.value.swipeConfig
            if (!config.enabled) return

            val statuses = _uiState.value.availableStatuses
            val targetStatus =
                resolveTargetStatus(config, statuses, direction) ?: run {
                    Timber.d("Swipe ignoré : aucun statut cible trouvé pour direction=$direction")
                    return
                }

            // Annule l'action précédente (sans appel API)
            pendingSwipeJob?.cancel()

            _uiState.update {
                it.copy(
                    pendingSwipeAction =
                        PendingSwipeAction(
                            orderId = orderId,
                            orderReference = orderReference,
                            targetStatusId = targetStatus.id,
                            targetStatusName = targetStatus.name,
                        ),
                )
            }

            pendingSwipeJob =
                viewModelScope.launch {
                    delay(SWIPE_UNDO_DELAY_MS)
                    // Fenêtre d'annulation écoulée → le changement devient effectif. On ferme la snackbar
                    // « Annuler » AVANT l'appel réseau : passé ce point, plus aucun clic « Annuler »
                    // trompeur (l'ancien code la laissait affichée pendant tout l'appel réseau, où
                    // cliquer « Annuler » ne changeait plus rien — cause du « ça n'a pas annulé »).
                    _uiState.update { it.copy(pendingSwipeAction = null) }
                    runCatching {
                        ordersRepository.updateOrderStatus(orderId, targetStatus.id.toString())
                    }.onFailure { error ->
                        Timber.w(error, "Swipe status update failed orderId=$orderId")
                        // La fenêtre d'annulation est passée : l'utilisateur croit la commande
                        // traitée. Un simple log serait silencieux — on DOIT le prévenir (canal
                        // snackbar existant, déjà utilisé par bulkUpdateStatus) qu'aucun changement
                        // n'est parti côté serveur pour cette commande.
                        _uiState.update {
                            it.copy(
                                bulkSnackbar = "Échec de la mise à jour de $orderReference : la commande n'a pas été modifiée",
                            )
                        }
                    }
                    refresh(forceRemote = true, notifyOnError = false)
                }
        }

        /**
         * Résout le statut cible en fonction de la direction et de la config swipe.
         *
         * - Si un ID est configuré et trouvé dans [statuses] → utilise cet ID.
         * - Sinon (null ou ID introuvable) → défaut par ID PrestaShop stable (gauche = En préparation,
         *   droite = Terminée avec repli Livré) — indépendant de la langue d'affichage des statuts.
         */
        internal fun resolveTargetStatus(
            config: SwipeConfig,
            statuses: List<com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter>,
            direction: SwipeDirection,
        ) = when (direction) {
            SwipeDirection.LEFT -> {
                val configuredId = config.leftTargetStatusId
                statuses.firstOrNull { it.id == (configuredId ?: SWIPE_DEFAULT_LEFT_TARGET_ID) }
                    ?: statuses.firstOrNull { it.id == SWIPE_DEFAULT_LEFT_TARGET_ID }
            }
            SwipeDirection.RIGHT -> {
                val configuredId = config.rightTargetStatusId
                statuses.firstOrNull { it.id == (configuredId ?: SWIPE_DEFAULT_RIGHT_TARGET_ID) }
                    ?: statuses.firstOrNull { it.id == SWIPE_DEFAULT_RIGHT_TARGET_ID }
                    ?: statuses.firstOrNull { it.id == SWIPE_DEFAULT_RIGHT_FALLBACK_ID }
            }
        }

        /**
         * Résout si une commande avec le statut [orderStatus] est éligible au swipe,
         * selon la config source.
         *
         * - Si [SwipeConfig.sourceStatusId] est configuré → compare par ID.
         * - Sinon → défaut par ID PrestaShop stable (Paiement accepté = 2), via [currentStateId] —
         *   plus de matching par nom FR (cassé en langue étrangère).
         */
        internal fun isSwipeSource(
            config: SwipeConfig,
            currentStateId: Int,
        ): Boolean {
            if (!config.enabled) return false
            return currentStateId == (config.sourceStatusId ?: SWIPE_DEFAULT_SOURCE_ID)
        }

        /** Annule l'action de swipe en attente (sans envoi API). */
        fun cancelSwipeAction() {
            pendingSwipeJob?.cancel()
            pendingSwipeJob = null
            _uiState.update { it.copy(pendingSwipeAction = null) }
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
    /** Action de swipe en attente (délai d'annulation). Null = aucune action en cours. */
    val pendingSwipeAction: PendingSwipeAction? = null,
    /** Configuration du swipe lue depuis les préférences persistées. */
    val swipeConfig: SwipeConfig = SwipeConfig(),
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
