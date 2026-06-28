package com.rebuildit.prestaflow.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardPreferencesRepository
import com.rebuildit.prestaflow.domain.dashboard.DashboardRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val dashboardRepository: DashboardRepository,
        private val dashboardPrefsRepository: DashboardPreferencesRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val selectedPeriodFlow = MutableStateFlow(DashboardPeriod.WEEK)

        /**
         * Plage de dates libre active (from ISO à to ISO).
         * `null` = mode preset ([selectedPeriodFlow] actif).
         */
        private val customRangeFlow = MutableStateFlow<Pair<String, String>?>(null)

        private val _uiState = MutableStateFlow(DashboardUiState())
        val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                // Lire la préférence de période par défaut AVANT de lancer les observations,
                // afin que la période initiale reflète le choix persisté de l'utilisateur.
                val defaultPeriod = dashboardPrefsRepository.defaultPeriod.first()
                selectedPeriodFlow.value = defaultPeriod
                _uiState.update { it.copy(selectedPeriod = defaultPeriod) }
                observeDashboard()
                refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = false)
                observeActiveShopSwitch()
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
            refresh(period, forceRemote = true, notifyOnError = true)
        }

        /**
         * Charge les métriques pour une plage de dates libre.
         *
         * @param from date de début `YYYY-MM-DD`
         * @param to   date de fin `YYYY-MM-DD`
         */
        fun onCustomRangeSelected(from: String, to: String) {
            _uiState.update {
                it.copy(
                    customRange = Pair(from, to),
                    isLoading = it.snapshot == null,
                    isRefreshing = true,
                    error = null,
                )
            }
            customRangeFlow.value = Pair(from, to)
            refreshCustom(from = from, to = to, notifyOnError = true)
        }

        fun onRefresh() {
            val custom = customRangeFlow.value
            if (custom != null) {
                refreshCustom(from = custom.first, to = custom.second, notifyOnError = true)
            } else {
                refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = true)
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
)
