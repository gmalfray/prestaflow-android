package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.dashboard.DashboardPreferencesRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Fake en mémoire de [DashboardPreferencesRepository] pour les tests unitaires. */
class FakeDashboardPreferencesRepository(
    initialPeriod: DashboardPeriod = DashboardPeriod.WEEK,
) : DashboardPreferencesRepository {
    private val periodFlow = MutableStateFlow(initialPeriod)

    override val defaultPeriod: Flow<DashboardPeriod> = periodFlow

    override suspend fun setDefaultPeriod(period: DashboardPeriod) {
        periodFlow.value = period
    }
}
