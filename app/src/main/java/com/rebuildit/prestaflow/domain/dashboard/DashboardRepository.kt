package com.rebuildit.prestaflow.domain.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardSnapshot
import kotlinx.coroutines.flow.Flow

interface DashboardRepository {
    fun observeDashboard(period: DashboardPeriod): Flow<DashboardSnapshot?>
    suspend fun refresh(period: DashboardPeriod, forceRemote: Boolean = false)
}
