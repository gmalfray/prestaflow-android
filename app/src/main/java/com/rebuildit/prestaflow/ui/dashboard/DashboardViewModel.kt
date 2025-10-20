package com.rebuildit.prestaflow.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.dashboard.DashboardRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val networkErrorMapper: NetworkErrorMapper
) : ViewModel() {

    private val selectedPeriodFlow = MutableStateFlow(DashboardPeriod.WEEK)
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeDashboard()
        refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = false)
    }

    fun onPeriodSelected(period: DashboardPeriod) {
        if (period == selectedPeriodFlow.value) return
        _uiState.update {
            it.copy(
                selectedPeriod = period,
                isLoading = it.snapshot == null,
                isRefreshing = true,
                error = null
            )
        }
        selectedPeriodFlow.value = period
        refresh(period, forceRemote = true, notifyOnError = true)
    }

    fun onRefresh() {
        refresh(selectedPeriodFlow.value, forceRemote = true, notifyOnError = true)
    }

    private fun observeDashboard() {
        viewModelScope.launch {
            selectedPeriodFlow
                .flatMapLatest { period ->
                    dashboardRepository.observeDashboard(period).map { snapshot ->
                        period to snapshot
                    }
                }
                .collect { (period, snapshot) ->
                    _uiState.update { current ->
                        val previousSnapshot =
                            if (current.selectedPeriod == period) current.snapshot else null
                        val resolvedSnapshot = snapshot ?: previousSnapshot
                        current.copy(
                            selectedPeriod = period,
                            snapshot = resolvedSnapshot,
                            isLoading = resolvedSnapshot == null && current.error == null,
                            isRefreshing = if (snapshot != null) false else current.isRefreshing,
                            error = if (snapshot != null) null else current.error
                        )
                    }
                }
        }
    }

    private fun refresh(period: DashboardPeriod, forceRemote: Boolean, notifyOnError: Boolean) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isRefreshing = true,
                    isLoading = current.snapshot == null,
                    error = if (notifyOnError) null else current.error
                )
            }

            val result = runCatching { dashboardRepository.refresh(period, forceRemote) }
            result
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            isLoading = current.snapshot == null && current.error == null
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
                                error = current.error
                            )
                        } else {
                            current.copy(
                                isRefreshing = false,
                                isLoading = false,
                                error = networkErrorMapper.map(error)
                            )
                        }
                    }
                }
        }
    }
}

data class DashboardUiState(
    val selectedPeriod: DashboardPeriod = DashboardPeriod.WEEK,
    val snapshot: DashboardSnapshot? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
)
