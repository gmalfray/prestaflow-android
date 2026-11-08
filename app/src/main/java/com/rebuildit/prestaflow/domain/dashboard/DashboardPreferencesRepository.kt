package com.rebuildit.prestaflow.domain.dashboard

import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import kotlinx.coroutines.flow.Flow

/** Préférences utilisateur liées au dashboard. */
interface DashboardPreferencesRepository {
    /**
     * Période par défaut appliquée à l'ouverture du dashboard.
     * Émet [DashboardPeriod.WEEK] si aucune préférence n'est enregistrée.
     */
    val defaultPeriod: Flow<DashboardPeriod>

    /** Persiste la période par défaut. */
    suspend fun setDefaultPeriod(period: DashboardPeriod)

    /** Courbe du graphique. Émet [DashboardChartMode.DEFAULT] si aucun choix n'est enregistré. */
    val chartMode: Flow<DashboardChartMode>

    /** Persiste la courbe choisie. */
    suspend fun setChartMode(mode: DashboardChartMode)
}
