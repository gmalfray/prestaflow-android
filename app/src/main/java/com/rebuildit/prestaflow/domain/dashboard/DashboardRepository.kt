package com.rebuildit.prestaflow.domain.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboard(period: DashboardPeriod): Flow<DashboardSnapshot?>

    /** Observe le cache d'une plage libre (clé `custom:<from>:<to>`). */
    fun observeCustomDashboard(from: String, to: String): Flow<DashboardSnapshot?>

    suspend fun refresh(
        period: DashboardPeriod,
        forceRemote: Boolean = false,
    )

    /**
     * Rafraîchit les métriques pour une plage de dates libre.
     *
     * @param from date de début au format `YYYY-MM-DD`
     * @param to   date de fin au format `YYYY-MM-DD`
     */
    suspend fun refreshCustom(
        from: String,
        to: String,
        forceRemote: Boolean = false,
    )
}
