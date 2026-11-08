package com.rebuildit.prestaflow.data.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rebuildit.prestaflow.data.dashboard.DashboardPreferencesRepositoryImpl
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Persistance DataStore réelle (fichier temporaire) : courbe du dashboard et option commandes offertes. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsPreferencesDataStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()

    private fun newStore() =
        PreferenceDataStoreFactory.create(scope = kotlinx.coroutines.CoroutineScope(dispatcher)) { tmp.newFile("prefs.preferences_pb") }

    @Test
    fun `notifyFreeOrders vaut true par défaut puis persiste le choix`() =
        runTest {
            val repo = NotificationCategoriesRepositoryImpl(newStore(), dispatcher)
            assertTrue(repo.isNotifyFreeOrders())
            repo.setNotifyFreeOrders(false)
            assertFalse(repo.isNotifyFreeOrders())
            assertFalse(repo.notifyFreeOrders.first())
        }

    @Test
    fun `courbe du dashboard - défaut CA vs N-1 puis choix persisté`() =
        runTest {
            val repo = DashboardPreferencesRepositoryImpl(newStore(), dispatcher)
            assertEquals(DashboardChartMode.TURNOVER_VS_PREVIOUS, repo.chartMode.first())
            repo.setChartMode(DashboardChartMode.PAID_VS_FREE)
            assertEquals(DashboardChartMode.PAID_VS_FREE, repo.chartMode.first())
        }

    @Test
    fun `valeur de courbe inconnue retombe sur le défaut`() {
        assertEquals(DashboardChartMode.DEFAULT, DashboardPreferencesRepositoryImpl.parseChartMode("OBSOLETE"))
        assertEquals(DashboardChartMode.DEFAULT, DashboardPreferencesRepositoryImpl.parseChartMode(null))
    }
}
