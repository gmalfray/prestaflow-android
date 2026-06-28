package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardPreferencesRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Vérifie que la période initiale du dashboard respecte la préférence persistée.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelDefaultPeriodTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        prefs: FakeDashboardPreferencesRepository,
    ): DashboardViewModel =
        DashboardViewModel(
            dashboardRepository = FakeDashboardRepository(),
            dashboardPrefsRepository = prefs,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = FakeAuthRepository(),
        )

    @Test
    fun `période initiale MONTH — selectedPeriod est MONTH`() = runTest {
        val vm = buildViewModel(FakeDashboardPreferencesRepository(DashboardPeriod.MONTH))
        advanceUntilIdle()
        assertEquals(DashboardPeriod.MONTH, vm.uiState.value.selectedPeriod)
    }

    @Test
    fun `période initiale TODAY — selectedPeriod est TODAY`() = runTest {
        val vm = buildViewModel(FakeDashboardPreferencesRepository(DashboardPeriod.TODAY))
        advanceUntilIdle()
        assertEquals(DashboardPeriod.TODAY, vm.uiState.value.selectedPeriod)
    }

    @Test
    fun `période initiale YEAR — selectedPeriod est YEAR`() = runTest {
        val vm = buildViewModel(FakeDashboardPreferencesRepository(DashboardPeriod.YEAR))
        advanceUntilIdle()
        assertEquals(DashboardPeriod.YEAR, vm.uiState.value.selectedPeriod)
    }

    @Test
    fun `période initiale QUARTER — selectedPeriod est QUARTER`() = runTest {
        val vm = buildViewModel(FakeDashboardPreferencesRepository(DashboardPeriod.QUARTER))
        advanceUntilIdle()
        assertEquals(DashboardPeriod.QUARTER, vm.uiState.value.selectedPeriod)
    }

    @Test
    fun `période initiale WEEK — selectedPeriod est WEEK (défaut)`() = runTest {
        val vm = buildViewModel(FakeDashboardPreferencesRepository(DashboardPeriod.WEEK))
        advanceUntilIdle()
        assertEquals(DashboardPeriod.WEEK, vm.uiState.value.selectedPeriod)
    }

    @Test
    fun `chip de session ne modifie pas la préférence persistée`() = runTest {
        val prefs = FakeDashboardPreferencesRepository(DashboardPeriod.MONTH)
        val vm = buildViewModel(prefs)
        advanceUntilIdle()

        // L'utilisateur tape un chip (choix de session uniquement)
        vm.onPeriodSelected(DashboardPeriod.WEEK)
        advanceUntilIdle()

        // La période de session change…
        assertEquals(DashboardPeriod.WEEK, vm.uiState.value.selectedPeriod)
        // …mais la préférence persistée reste inchangée
        assertEquals(DashboardPeriod.MONTH, prefs.defaultPeriod.first())
    }
}
