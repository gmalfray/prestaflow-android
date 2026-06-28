package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.dashboard.DashboardRepository
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartPoint
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [DashboardRepository].
 * Permet de contrôler les snapshots émis et d'inspecter les appels faits par le ViewModel.
 */
class FakeDashboardRepository : DashboardRepository {
    private val snapshotByPeriod = mutableMapOf<String, MutableStateFlow<DashboardSnapshot?>>()
    private val snapshotByCustom = mutableMapOf<String, MutableStateFlow<DashboardSnapshot?>>()

    /** Dernier appel à [refresh] — (period, forceRemote). */
    var lastRefreshCall: Pair<DashboardPeriod, Boolean>? = null

    /** Dernier appel à [refreshCustom] — (from, to). */
    var lastRefreshCustomCall: Pair<String, String>? = null

    /** Exception à lancer lors du prochain appel à [refresh]. Null = succès. */
    var refreshException: Throwable? = null

    /** Exception à lancer lors du prochain appel à [refreshCustom]. Null = succès. */
    var refreshCustomException: Throwable? = null

    private fun flowForPeriod(period: DashboardPeriod) =
        snapshotByPeriod.getOrPut(period.name) { MutableStateFlow(null) }

    private fun flowForCustom(from: String, to: String) =
        snapshotByCustom.getOrPut("$from:$to") { MutableStateFlow(null) }

    override fun observeDashboard(period: DashboardPeriod): Flow<DashboardSnapshot?> =
        flowForPeriod(period)

    override fun observeCustomDashboard(from: String, to: String): Flow<DashboardSnapshot?> =
        flowForCustom(from, to)

    override suspend fun refresh(period: DashboardPeriod, forceRemote: Boolean) {
        lastRefreshCall = Pair(period, forceRemote)
        refreshException?.let { throw it }
    }

    override suspend fun refreshCustom(from: String, to: String, forceRemote: Boolean) {
        lastRefreshCustomCall = Pair(from, to)
        refreshCustomException?.let { throw it }
    }

    /** Émet un snapshot pour un preset period donné. */
    fun emitSnapshot(period: DashboardPeriod, snapshot: DashboardSnapshot?) {
        flowForPeriod(period).value = snapshot
    }

    /** Émet un snapshot pour une plage custom. */
    fun emitCustomSnapshot(from: String, to: String, snapshot: DashboardSnapshot?) {
        flowForCustom(from, to).value = snapshot
    }

    companion object {
        fun fakeSnapshot(period: DashboardPeriod = DashboardPeriod.WEEK): DashboardSnapshot =
            DashboardSnapshot(
                period = period,
                turnover = 1000.0,
                ordersCount = 10,
                customersCount = 5,
                productsCount = 50,
                chart = listOf(
                    DashboardChartPoint(label = "L", orders = 5, customers = 2, turnover = 400.0, newCustomers = 1),
                    DashboardChartPoint(label = "M", orders = 5, customers = 3, turnover = 600.0, newCustomers = 2),
                ),
                lastUpdatedIso = "2026-06-01T10:00:00Z",
            )
    }
}
