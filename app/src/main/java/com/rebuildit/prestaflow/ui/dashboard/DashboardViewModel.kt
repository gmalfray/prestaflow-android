package com.rebuildit.prestaflow.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardPreferencesRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardWidgetsRepository
import com.rebuildit.prestaflow.domain.dashboard.model.AbandonedCartsSummary
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversion
import com.rebuildit.prestaflow.domain.dashboard.model.LatestOrder
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Suppress("LongParameterList") // dépendances injectées par Hilt
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val dashboardRepository: DashboardRepository,
        private val dashboardPrefsRepository: DashboardPreferencesRepository,
        private val widgetsRepository: DashboardWidgetsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
        private val resumeRefreshGuard: ScreenResumeRefreshGuard,
    ) : ViewModel() {
        private val selectedPeriodFlow = MutableStateFlow(DashboardPeriod.WEEK)

        /**
         * Plage de dates libre active (from ISO à to ISO).
         * `null` = mode preset ([selectedPeriodFlow] actif).
         */
        private val customRangeFlow = MutableStateFlow<Pair<String, String>?>(null)

        private val widgetJobs = mutableMapOf<DashboardWidget, Job>()
        private var previouslyVisible: Set<DashboardWidget> = emptySet()

        private val _uiState = MutableStateFlow(DashboardUiState())
        val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                // Lire la préférence de période par défaut AVANT de lancer les observations,
                // afin que la période initiale reflète le choix persisté de l'utilisateur.
                val defaultPeriod = dashboardPrefsRepository.defaultPeriod.first()
                selectedPeriodFlow.value = defaultPeriod
                _uiState.update { it.copy(selectedPeriod = defaultPeriod) }
                observeChartMode()
                observeWidgetLayout()
                observeDashboard()
                loadConversion()
                refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = false)
                observeActiveShopSwitch()
            }
        }

        fun onChartModeSelected(mode: DashboardChartMode) {
            _uiState.update { it.copy(chartMode = mode) }
            viewModelScope.launch { dashboardPrefsRepository.setChartMode(mode) }
        }

        fun onWidgetLayoutSaved(layout: DashboardLayout) {
            _uiState.update { it.copy(widgetLayout = layout) }
            viewModelScope.launch { dashboardPrefsRepository.setWidgetLayout(layout) }
        }

        private fun observeWidgetLayout() {
            viewModelScope.launch {
                dashboardPrefsRepository.widgetLayout.collect { layout ->
                    _uiState.update { it.copy(widgetLayout = layout) }
                    val visible = layout.visibleWidgets.toSet()
                    (visible - previouslyVisible).forEach(::loadWidget)
                    previouslyVisible = visible
                }
            }
        }

        fun onWidgetRetry(widget: DashboardWidget) = loadWidget(widget)

        private fun loadVisibleWidgets() {
            _uiState.value.widgetLayout.visibleWidgets.forEach(::loadWidget)
        }

        private fun loadWidget(widget: DashboardWidget) {
            when (widget) {
                DashboardWidget.LATEST_ORDERS ->
                    launchWidgetLoad(widget, { it.latestOrders }, { s, v -> s.copy(latestOrders = v) }) {
                        widgetsRepository.getLatestOrders(LATEST_ORDERS_LIMIT)
                    }
                DashboardWidget.CARTS ->
                    launchWidgetLoad(widget, { it.abandonedCarts }, { s, v -> s.copy(abandonedCarts = v) }) {
                        widgetsRepository.getAbandonedCarts(ABANDONED_CARTS_DAYS)
                    }
                DashboardWidget.REVIEWS ->
                    launchWidgetLoad(widget, { it.pendingReviews }, { s, v -> s.copy(pendingReviews = v) }) {
                        widgetsRepository.getPendingReviews()
                    }
                DashboardWidget.TOP_PRODUCTS ->
                    launchWidgetLoad(widget, { it.topProducts }, { s, v -> s.copy(topProducts = v) }) {
                        val (from, to) = periodDateRange(selectedPeriodFlow.value, customRangeFlow.value)
                        widgetsRepository.getTopProducts(TOP_PRODUCTS_LIMIT, from, to)
                    }
                DashboardWidget.SALES_BY_UNIVERSE ->
                    launchWidgetLoad(widget, { it.salesByUniverse }, { s, v -> s.copy(salesByUniverse = v) }) {
                        val (from, to) = periodDateRange(selectedPeriodFlow.value, customRangeFlow.value)
                        widgetsRepository.getSalesByUnivers(from, to)
                    }
                else -> Unit
            }
        }

        /**
         * Charge un widget isolé : un échec ne touche que son état. Les données déjà affichées sont
         * conservées pendant le rechargement. [fetch] renvoie `null` quand la fonctionnalité est absente.
         */
        private fun <T : Any> launchWidgetLoad(
            widget: DashboardWidget,
            read: (DashboardUiState) -> WidgetLoad<T>,
            write: (DashboardUiState, WidgetLoad<T>) -> DashboardUiState,
            fetch: suspend () -> T?,
        ) {
            widgetJobs[widget]?.cancel()
            widgetJobs[widget] =
                viewModelScope.launch {
                    if (read(_uiState.value) !is WidgetLoad.Data) {
                        _uiState.update { write(it, WidgetLoad.Loading) }
                    }
                    runCatching { fetch() }
                        .onSuccess { value ->
                            _uiState.update { write(it, value?.let { v -> WidgetLoad.Data(v) } ?: WidgetLoad.Unavailable) }
                        }
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            Timber.w(error, "Failed to load dashboard widget %s", widget.id)
                            _uiState.update { write(it, WidgetLoad.Error) }
                        }
                }
        }

        private fun reloadPeriodWidgets() {
            val visible = _uiState.value.widgetLayout.visibleWidgets
            listOf(DashboardWidget.TOP_PRODUCTS, DashboardWidget.SALES_BY_UNIVERSE)
                .filter { it in visible }
                .forEach(::loadWidget)
        }

        private fun observeChartMode() {
            viewModelScope.launch {
                dashboardPrefsRepository.chartMode.collect { mode ->
                    _uiState.update { it.copy(chartMode = mode) }
                }
            }
        }

        /**
         * Charge la carte « conversion offert → payant » (tout l'historique). Isolée du reste :
         * un échec n'affecte ni le dashboard ni son bandeau d'erreur, seulement la carte.
         */
        fun loadConversion() {
            viewModelScope.launch {
                _uiState.update { it.copy(conversion = ConversionState.Loading) }
                runCatching { dashboardRepository.getFreeConversion() }
                    .onSuccess { data -> _uiState.update { it.copy(conversion = ConversionState.Data(data)) } }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Timber.w(error, "Failed to load free conversion")
                        _uiState.update { it.copy(conversion = ConversionState.Error) }
                    }
            }
        }

        fun onPeriodSelected(period: DashboardPeriod) {
            if (period == selectedPeriodFlow.value && customRangeFlow.value == null) return
            _uiState.update {
                it.copy(
                    selectedPeriod = period,
                    customRange = null,
                    isLoading = it.snapshot == null,
                    isRefreshing = true,
                    error = null,
                )
            }
            customRangeFlow.value = null
            selectedPeriodFlow.value = period
            reloadPeriodWidgets()
            refresh(period, forceRemote = true, notifyOnError = true)
        }

        /**
         * Charge les métriques pour une plage de dates libre.
         *
         * @param from date de début `YYYY-MM-DD`
         * @param to   date de fin `YYYY-MM-DD`
         */
        fun onCustomRangeSelected(
            from: String,
            to: String,
        ) {
            _uiState.update {
                it.copy(
                    customRange = Pair(from, to),
                    isLoading = it.snapshot == null,
                    isRefreshing = true,
                    error = null,
                )
            }
            customRangeFlow.value = Pair(from, to)
            reloadPeriodWidgets()
            refreshCustom(from = from, to = to, notifyOnError = true)
        }

        fun onRefresh() {
            loadConversion()
            loadVisibleWidgets()
            val custom = customRangeFlow.value
            if (custom != null) {
                refreshCustom(from = custom.first, to = custom.second, notifyOnError = true)
            } else {
                refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = true)
            }
        }

        /**
         * Rattrapage au retour sur l'écran Accueil (cf. KDoc de
         * [com.rebuildit.prestaflow.ui.orders.OrdersViewModel.onScreenResumed] pour le contrat
         * général porté par [resumeRefreshGuard]) : typiquement après un détour par le détail
         * d'une commande ouvert depuis une notification de vente.
         *
         * Rafraîchit la période preset OU la plage libre active, selon le mode courant. Silencieux
         * (`notifyOnError = false`) : en cas d'échec réseau le cache existant reste affiché. Le
         * geste manuel [onRefresh] n'est jamais throttlé.
         */
        fun onScreenResumed() {
            if (!resumeRefreshGuard.shouldRefresh(isBusy = _uiState.value.isRefreshing)) return
            loadVisibleWidgets()
            val custom = customRangeFlow.value
            if (custom != null) {
                refreshCustom(from = custom.first, to = custom.second, notifyOnError = false)
            } else {
                refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = false)
            }
        }

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1) // ignore la valeur initiale déjà traitée dans init
                    .collect {
                        // Réinitialise l'état et recharge pour la nouvelle boutique.
                        _uiState.update { current ->
                            current.copy(snapshot = null, customRange = null, isLoading = true, error = null)
                        }
                        customRangeFlow.value = null
                        _uiState.update {
                            it.copy(
                                latestOrders = WidgetLoad.Idle,
                                abandonedCarts = WidgetLoad.Idle,
                                pendingReviews = WidgetLoad.Idle,
                                topProducts = WidgetLoad.Idle,
                                salesByUniverse = WidgetLoad.Idle,
                            )
                        }
                        loadVisibleWidgets()
                        refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = true)
                    }
            }
        }

        private fun observeDashboard() {
            viewModelScope.launch {
                combine(selectedPeriodFlow, customRangeFlow) { period, customRange ->
                    period to customRange
                }.flatMapLatest { (period, customRange) ->
                    if (customRange != null) {
                        dashboardRepository.observeCustomDashboard(customRange.first, customRange.second)
                            .map { snapshot -> Triple(period, customRange, snapshot) }
                    } else {
                        dashboardRepository.observeDashboard(period)
                            .map { snapshot -> Triple(period, null as Pair<String, String>?, snapshot) }
                    }
                }.collect { (period, customRange, snapshot) ->
                    _uiState.update { current ->
                        // Garde l'ancien snapshot si on est toujours sur la même sélection
                        // (pour éviter un flash "vide" lors du rechargement).
                        val sameSelection =
                            current.selectedPeriod == period && current.customRange == customRange
                        val previousSnapshot = if (sameSelection) current.snapshot else null
                        val resolvedSnapshot = snapshot ?: previousSnapshot
                        current.copy(
                            selectedPeriod = period,
                            customRange = customRange,
                            snapshot = resolvedSnapshot,
                            isLoading = resolvedSnapshot == null && current.error == null,
                            isRefreshing = if (snapshot != null) false else current.isRefreshing,
                            error = if (snapshot != null) null else current.error,
                        )
                    }
                }
            }
        }

        private fun refresh(
            period: DashboardPeriod,
            forceRemote: Boolean,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.snapshot == null,
                        error = if (notifyOnError) null else current.error,
                    )
                }

                val result = runCatching { dashboardRepository.refresh(period, forceRemote) }
                result
                    .onSuccess {
                        resumeRefreshGuard.markRefreshSucceeded()
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.snapshot == null && current.error == null,
                            )
                        }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh dashboard for %s", period)
                        _uiState.update { current ->
                            if (!notifyOnError) {
                                current.copy(
                                    isRefreshing = false,
                                    isLoading = current.snapshot == null,
                                    error = current.error,
                                )
                            } else {
                                current.copy(
                                    isRefreshing = false,
                                    isLoading = false,
                                    error = networkErrorMapper.map(error),
                                )
                            }
                        }
                    }
            }
        }

        private fun refreshCustom(
            from: String,
            to: String,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.snapshot == null,
                        error = if (notifyOnError) null else current.error,
                    )
                }

                val result = runCatching { dashboardRepository.refreshCustom(from, to, forceRemote = true) }
                result
                    .onSuccess {
                        resumeRefreshGuard.markRefreshSucceeded()
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.snapshot == null && current.error == null,
                            )
                        }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh dashboard for custom range %s–%s", from, to)
                        _uiState.update { current ->
                            if (!notifyOnError) {
                                current.copy(
                                    isRefreshing = false,
                                    isLoading = current.snapshot == null,
                                    error = current.error,
                                )
                            } else {
                                current.copy(
                                    isRefreshing = false,
                                    isLoading = false,
                                    error = networkErrorMapper.map(error),
                                )
                            }
                        }
                    }
            }
        }
    }

data class DashboardUiState(
    val selectedPeriod: DashboardPeriod = DashboardPeriod.WEEK,
    /**
     * Plage de dates libre active (from ISO, to ISO).
     * `null` = mode preset ([selectedPeriod] actif).
     */
    val customRange: Pair<String, String>? = null,
    val snapshot: DashboardSnapshot? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    /** Courbe du graphique (préférence persistée). */
    val chartMode: DashboardChartMode = DashboardChartMode.DEFAULT,
    /** Carte conversion offert → payant, indépendante du chargement principal. */
    val conversion: ConversionState = ConversionState.Loading,
    /** Disposition des widgets (préférence persistée). */
    val widgetLayout: DashboardLayout = DashboardLayout.DEFAULT,
    val latestOrders: WidgetLoad<List<LatestOrder>> = WidgetLoad.Idle,
    val abandonedCarts: WidgetLoad<AbandonedCartsSummary> = WidgetLoad.Idle,
    val pendingReviews: WidgetLoad<PendingReviewsCount> = WidgetLoad.Idle,
    val topProducts: WidgetLoad<List<TopProduct>> = WidgetLoad.Idle,
    val salesByUniverse: WidgetLoad<SalesByUniverse> = WidgetLoad.Idle,
)

private const val LATEST_ORDERS_LIMIT = 5
private const val ABANDONED_CARTS_DAYS = 7
private const val TOP_PRODUCTS_LIMIT = 5

/** État de la carte conversion offert → payant. */
sealed interface ConversionState {
    data object Loading : ConversionState

    data object Error : ConversionState

    data class Data(val conversion: FreeConversion) : ConversionState
}
