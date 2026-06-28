package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val TOP_CLIENTS_LIMIT = 20
private const val SEARCH_DEBOUNCE_MS = 300L
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Mode d'affichage de la liste clients.
 *
 * - [TOP_CLIENTS] : mode par défaut — les 20 meilleurs acheteurs (`customers/top`), cachés Room.
 * - [ALL_CLIENTS] : liste complète paginée (`GET customers?sort=date_desc`). Activé par la carte « Total clients ».
 * - [NEW_THIS_MONTH] : nouveaux inscrits ce mois civil (`GET customers?filter[created_from]=...`). Activé par « Nouveaux du mois ».
 */
enum class ClientFilter { TOP_CLIENTS, ALL_CLIENTS, NEW_THIS_MONTH }

@OptIn(FlowPreview::class)
@HiltViewModel
class ClientsViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val clientsRepository: ClientsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        /**
         * Filtre initial transmis par la navigation (arg "filter").
         * "new" → [ClientFilter.NEW_THIS_MONTH] quand on arrive depuis le KPI "Nouveaux clients" du dashboard.
         * Null (accès direct via la barre de navigation) → [ClientFilter.TOP_CLIENTS].
         */
        private val initialFilter: ClientFilter =
            when (savedStateHandle.get<String?>("filter")) {
                "new" -> ClientFilter.NEW_THIS_MONTH
                else -> ClientFilter.TOP_CLIENTS
            }

        /**
         * Argument de navigation "filter" exposé comme [StateFlow] réactif.
         *
         * Navigation Component met à jour le [SavedStateHandle] quand il navigue vers
         * cette destination avec `launchSingleTop = true` et un nouvel argument. En observant
         * ce flux dans [ClientsRoute] via un `LaunchedEffect`, on ré-applique le mode
         * [ClientFilter.NEW_THIS_MONTH] même si le ViewModel est réutilisé (ViewModel conservé
         * dans la back-stack sauvegardée puis restauré par `restoreState = true`).
         */
        val navigationFilterFlow: StateFlow<String?> =
            savedStateHandle.getStateFlow<String?>("filter", null)
                .stateIn(viewModelScope, SharingStarted.Eagerly, savedStateHandle.get<String?>("filter"))

        private val _uiState = MutableStateFlow(ClientsUiState(activeFilter = initialFilter))
        val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

        /** Flow interne de la query de recherche, débouncé avant d'émettre une requête. */
        private val _searchQuery = MutableStateFlow("")

        /** Job de la coroutine de chargement en cours (annulable). */
        private var loadJob: Job? = null

        init {
            observeTopClients()
            loadInitialData()
            observeActiveShopSwitch()
            observeSearchQuery()
        }

        // ─── Actions publiques ────────────────────────────────────────────────

        fun onRefresh() {
            val mode = _uiState.value.activeFilter
            val query = _uiState.value.query
            if (query.isNotBlank() || mode != ClientFilter.TOP_CLIENTS) {
                fetchClientsForMode(mode = mode, query = query, resetPage = true, notifyOnError = true)
            } else {
                refreshTopClientsAndStats(notifyOnError = true)
            }
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
            _searchQuery.value = query
        }

        /**
         * Active ou désactive un mode de liste via les cartes KPI.
         * - Tap sur une carte inactive → active ce mode.
         * - Tap sur la carte déjà active → revient au mode [ClientFilter.TOP_CLIENTS].
         * - Tap sur [ClientFilter.TOP_CLIENTS] → toujours [ClientFilter.TOP_CLIENTS] (mode neutre).
         */
        fun onFilterChange(filter: ClientFilter) {
            val currentMode = _uiState.value.activeFilter
            val newMode =
                when {
                    filter == ClientFilter.TOP_CLIENTS -> ClientFilter.TOP_CLIENTS
                    currentMode == filter -> ClientFilter.TOP_CLIENTS // re-tap = retour défaut
                    else -> filter
                }

            if (newMode == currentMode) return

            val query = _uiState.value.query
            _uiState.update { current ->
                current.copy(
                    activeFilter = newMode,
                    clients = if (newMode == ClientFilter.TOP_CLIENTS) current.clients else emptyList(),
                    hasNextPage = false,
                    nextOffset = 0,
                    isLoadingMore = false,
                )
            }

            if (newMode == ClientFilter.TOP_CLIENTS) {
                observeTopClients()
            } else {
                fetchClientsForMode(mode = newMode, query = query, resetPage = true, notifyOnError = true)
            }
        }

        /**
         * Applique le filtre transmis par la navigation **sans logique de toggle**.
         *
         * Appelé depuis [ClientsRoute] via `LaunchedEffect` chaque fois que l'argument
         * de navigation "filter" change (y compris lors d'une ré-navigation sur un ViewModel
         * existant via `launchSingleTop`). Idempotent : sans effet si le mode est déjà correct.
         *
         * Ne pas confondre avec [onFilterChange] qui implémente le toggle KPI (tap = activer,
         * re-tap = retour TOP) utilisé par les cartes de l'écran Clients lui-même.
         */
        fun onNavigationFilter(filter: ClientFilter) {
            val currentMode = _uiState.value.activeFilter
            if (currentMode == filter) return
            val query = _uiState.value.query
            _uiState.update { current ->
                current.copy(
                    activeFilter = filter,
                    clients = if (filter == ClientFilter.TOP_CLIENTS) current.clients else emptyList(),
                    hasNextPage = false,
                    nextOffset = 0,
                    isLoadingMore = false,
                )
            }
            if (filter == ClientFilter.TOP_CLIENTS) {
                observeTopClients()
            } else {
                fetchClientsForMode(mode = filter, query = query, resetPage = true, notifyOnError = true)
            }
        }

        /** Charge la page suivante (mode ALL ou NEW_THIS_MONTH uniquement). */
        fun onLoadMore() {
            val state = _uiState.value
            if (!state.hasNextPage || state.isLoadingMore || state.activeFilter == ClientFilter.TOP_CLIENTS) return
            fetchClientsForMode(
                mode = state.activeFilter,
                query = state.query,
                resetPage = false,
                notifyOnError = true,
            )
        }

        // ─── Initialisations ─────────────────────────────────────────────────

        private fun loadInitialData() {
            viewModelScope.launch {
                if (initialFilter == ClientFilter.TOP_CLIENTS) {
                    _uiState.update { it.copy(isRefreshing = true, isLoading = it.clients.isEmpty()) }
                    val topResult = runCatching { clientsRepository.refreshTopClients(TOP_CLIENTS_LIMIT, forceRemote = true) }
                    val stats = clientsRepository.fetchStats()
                    topResult.onFailure { error ->
                        Timber.w(error, "Échec du chargement initial des clients")
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.clients.isEmpty(),
                                error = networkErrorMapper.map(error),
                                stats = stats ?: current.stats,
                            )
                        }
                    }.onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.clients.isEmpty(),
                                error = null,
                                stats = stats ?: current.stats,
                            )
                        }
                    }
                } else {
                    // Navigation depuis le dashboard avec filtre (ex. "new") :
                    // charger les stats puis déléguer au chargement filtré standard.
                    val stats = clientsRepository.fetchStats()
                    _uiState.update { current -> current.copy(stats = stats ?: current.stats) }
                    fetchClientsForMode(mode = initialFilter, query = "", resetPage = true, notifyOnError = false)
                }
            }
        }

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(
                                clients = emptyList(),
                                stats = null,
                                isLoading = true,
                                error = null,
                                activeFilter = ClientFilter.TOP_CLIENTS,
                                hasNextPage = false,
                                nextOffset = 0,
                                query = "",
                            )
                        }
                        _searchQuery.value = ""
                        loadInitialData()
                        observeTopClients()
                    }
            }
        }

        private fun observeTopClients() {
            viewModelScope.launch {
                clientsRepository.observeTopClients(TOP_CLIENTS_LIMIT).collect { clients ->
                    // Ne met à jour la liste que si on est en mode TOP_CLIENTS et pas de recherche active
                    if (_uiState.value.activeFilter == ClientFilter.TOP_CLIENTS && _uiState.value.query.isBlank()) {
                        _uiState.update { current ->
                            current.copy(
                                clients = clients,
                                isLoading = false,
                                isRefreshing = false,
                                error = if (clients.isNotEmpty()) null else current.error,
                            )
                        }
                    }
                }
            }
        }

        private fun observeSearchQuery() {
            viewModelScope.launch {
                _searchQuery
                    .debounce(SEARCH_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { query ->
                        if (query.isBlank() && _uiState.value.activeFilter == ClientFilter.TOP_CLIENTS) {
                            // Retour à la liste top clients depuis Room (déjà observée)
                            return@collect
                        }
                        fetchClientsForMode(
                            mode = _uiState.value.activeFilter,
                            query = query,
                            resetPage = true,
                            notifyOnError = false,
                        )
                    }
            }
        }

        // ─── Chargement réseau ────────────────────────────────────────────────

        private fun refreshTopClientsAndStats(notifyOnError: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _uiState.update { current ->
                        current.copy(isRefreshing = true, error = if (notifyOnError) null else current.error)
                    }
                    val topResult = runCatching { clientsRepository.refreshTopClients(TOP_CLIENTS_LIMIT, forceRemote = true) }
                    val stats = clientsRepository.fetchStats()
                    topResult.onFailure { error ->
                        Timber.w(error, "Échec du rafraîchissement des top clients")
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                error = if (notifyOnError) networkErrorMapper.map(error) else current.error,
                                stats = stats ?: current.stats,
                            )
                        }
                    }.onSuccess {
                        _uiState.update { current ->
                            current.copy(isRefreshing = false, error = null, stats = stats ?: current.stats)
                        }
                    }
                }
        }

        /**
         * Charge ou recharge la liste selon le [mode] et la [query].
         *
         * - [resetPage] = true → repart de l'offset 0 et remplace la liste.
         * - [resetPage] = false → ajoute en bas (pagination incrémentale).
         */
        private fun fetchClientsForMode(
            mode: ClientFilter,
            query: String,
            resetPage: Boolean,
            notifyOnError: Boolean,
        ) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val currentState = _uiState.value
                    val offset = if (resetPage) 0 else currentState.nextOffset

                    if (resetPage) {
                        _uiState.update { it.copy(isLoading = it.clients.isEmpty(), isRefreshing = it.clients.isNotEmpty(), error = null) }
                    } else {
                        _uiState.update { it.copy(isLoadingMore = true) }
                    }

                    val (sort, createdFrom) = modeToParams(mode)

                    runCatching {
                        clientsRepository.fetchClients(
                            query = query.takeIf { it.isNotBlank() },
                            sort = sort,
                            createdFrom = createdFrom,
                            limit = ClientsRepository.PAGE_SIZE,
                            offset = offset,
                        )
                    }.onSuccess { page ->
                        _uiState.update { current ->
                            val mergedClients =
                                if (resetPage) page.clients else current.clients + page.clients
                            current.copy(
                                clients = mergedClients,
                                hasNextPage = page.hasNext,
                                nextOffset = page.nextOffset,
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = null,
                            )
                        }
                    }.onFailure { error ->
                        Timber.w(error, "Échec du chargement des clients (mode=$mode, offset=$offset)")
                        _uiState.update { current ->
                            current.copy(
                                isLoading = false,
                                isRefreshing = false,
                                isLoadingMore = false,
                                error = if (notifyOnError) networkErrorMapper.map(error) else current.error,
                            )
                        }
                    }
                }
        }

        /**
         * Traduit un [ClientFilter] en paramètres réseau `(sort, createdFrom)`.
         *
         * Pour [ClientFilter.NEW_THIS_MONTH], calcule dynamiquement le 1er du mois courant
         * (fuseau local de l'appareil) au format `yyyy-MM-dd`.
         */
        private fun modeToParams(mode: ClientFilter): Pair<String?, String?> =
            when (mode) {
                ClientFilter.TOP_CLIENTS -> null to null
                ClientFilter.ALL_CLIENTS -> "date_desc" to null
                ClientFilter.NEW_THIS_MONTH -> {
                    val firstOfMonth = LocalDate.now().withDayOfMonth(1).format(DATE_FORMATTER)
                    "date_desc" to firstOfMonth
                }
            }
    }

data class ClientsUiState(
    val clients: List<Client> = emptyList(),
    /**
     * Statistiques agrégées depuis [GET customers/stats].
     * Null tant que la requête n'a pas abouti (état initial ou erreur).
     */
    val stats: ClientStats? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** `true` pendant le chargement de la page suivante (infinite scroll). */
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    /** Mode actif piloté par les cartes KPI. */
    val activeFilter: ClientFilter = ClientFilter.TOP_CLIENTS,
    /** `true` si une page suivante est disponible (modes ALL / NEW). */
    val hasNextPage: Boolean = false,
    /** Offset à passer pour la prochaine page. */
    val nextOffset: Int = 0,
) {
    /**
     * Titre de la section liste, dépend du mode actif et de la query de recherche.
     *
     * Non-nul seulement dans les cas où la chaîne doit être générée ici ; l'écran
     * mappe ce champ vers la chaîne localisée correcte.
     */
    val listMode: ClientListMode
        get() =
            when {
                query.isNotBlank() -> ClientListMode.SEARCH
                activeFilter == ClientFilter.ALL_CLIENTS -> ClientListMode.ALL
                activeFilter == ClientFilter.NEW_THIS_MONTH -> ClientListMode.NEW
                else -> ClientListMode.TOP
            }

    /**
     * Les clients visibles = toujours [clients] (le filtrage est désormais côté serveur).
     * Le tri local n'est pas appliqué car l'API retourne déjà les résultats dans l'ordre demandé.
     */
    val visibleClients: List<Client>
        get() = clients
}

/** Mode d'affichage de la liste, utilisé par l'UI pour déterminer le titre de section et l'état « charger plus ». */
enum class ClientListMode { TOP, ALL, NEW, SEARCH }
