package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.dashboard.DashboardPreferencesRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake en mémoire de [DashboardPreferencesRepository] pour les tests unitaires. */
class FakeDashboardPreferencesRepository(
    initialPeriod: DashboardPeriod = DashboardPeriod.WEEK,
) : DashboardPreferencesRepository {
    private val periodFlow = MutableStateFlow(initialPeriod)

    override val defaultPeriod: Flow<DashboardPeriod> = periodFlow

    private val chartModeFlow = MutableStateFlow(DashboardChartMode.DEFAULT)

    override val chartMode: Flow<DashboardChartMode> = chartModeFlow

    override suspend fun setChartMode(mode: DashboardChartMode) {
        chartModeFlow.value = mode
    }

    override suspend fun setDefaultPeriod(period: DashboardPeriod) {
        periodFlow.value = period
    }
}
