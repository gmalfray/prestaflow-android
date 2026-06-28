package com.rebuildit.prestaflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.dashboard.DashboardPreferencesRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardPrefsViewModel
    @Inject
    constructor(
        private val dashboardPrefsRepository: DashboardPreferencesRepository,
    ) : ViewModel() {
        val defaultPeriod: StateFlow<DashboardPeriod> =
            dashboardPrefsRepository.defaultPeriod
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DashboardPeriod.WEEK,
                )

        fun setDefaultPeriod(period: DashboardPeriod) {
            viewModelScope.launch {
                dashboardPrefsRepository.setDefaultPeriod(period)
            }
        }
    }
