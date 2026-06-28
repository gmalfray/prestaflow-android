package com.rebuildit.prestaflow.ui.dashboard

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardPreferencesRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [DashboardViewModel].
 *
 * Vérifie le comportement des sélections de période (preset vs plage libre) :
 * - La plage libre est reflétée dans [DashboardUiState.customRange].
 * - Le retour à un preset efface [DashboardUiState.customRange].
 * - Le repo est appelé avec les bons paramètres selon le mode actif.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeDashboardRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakePrefs: FakeDashboardPreferencesRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeDashboardRepository()
        fakeAuth = FakeAuthRepository()
        fakePrefs = FakeDashboardPreferencesRepository(initialPeriod = DashboardPeriod.WEEK)
        viewModel = DashboardViewModel(
            dashboardRepository = fakeRepo,
            dashboardPrefsRepository = fakePrefs,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = fakeAuth,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `état initial — selectedPeriod WEEK, customRange null`() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(DashboardPeriod.WEEK, state.selectedPeriod)
        assertNull(state.customRange)
    }

    @Test
    fun `onCustomRangeSelected — customRange reflété dans UiState`() = runTest {
        viewModel.onCustomRangeSelected("2026-05-01", "2026-05-31")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Pair("2026-05-01", "2026-05-31"), state.customRange)
    }

    @Test
    fun `onCustomRangeSelected — refreshCustom appelé avec les bons paramètres`() = runTest {
        viewModel.onCustomRangeSelected("2026-05-01", "2026-05-31")
        advanceUntilIdle()

        assertEquals(
            Pair("2026-05-01", "2026-05-31"),
            fakeRepo.lastRefreshCustomCall,
        )
    }

    @Test
    fun `onPeriodSelected après customRange — customRange remis à null`() = runTest {
        viewModel.onCustomRangeSelected("2026-05-01", "2026-05-31")
        advanceUntilIdle()

        viewModel.onPeriodSelected(DashboardPeriod.MONTH)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DashboardPeriod.MONTH, state.selectedPeriod)
        assertNull(state.customRange)
    }

    @Test
    fun `snapshot custom reçu — reflété dans UiState`() = runTest {
        viewModel.onCustomRangeSelected("2026-05-01", "2026-05-31")
        advanceUntilIdle()

        val expected = FakeDashboardRepository.fakeSnapshot()
        fakeRepo.emitCustomSnapshot("2026-05-01", "2026-05-31", expected)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(expected.turnover, state.snapshot?.turnover)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onRefresh en mode custom — appelle refreshCustom`() = runTest {
        viewModel.onCustomRangeSelected("2026-01-01", "2026-01-31")
        advanceUntilIdle()
        fakeRepo.lastRefreshCustomCall = null // reset après l'appel initial

        viewModel.onRefresh()
        advanceUntilIdle()

        assertNotNull(fakeRepo.lastRefreshCustomCall)
        assertEquals(Pair("2026-01-01", "2026-01-31"), fakeRepo.lastRefreshCustomCall)
    }

    @Test
    fun `onRefresh en mode preset — appelle refresh preset`() = runTest {
        advanceUntilIdle()
        fakeRepo.lastRefreshCall = null // reset après l'appel initial dans init

        viewModel.onRefresh()
        advanceUntilIdle()

        assertNotNull(fakeRepo.lastRefreshCall)
        assertEquals(DashboardPeriod.WEEK, fakeRepo.lastRefreshCall?.first)
    }

    @Test
    fun `newCustomers présent dans les points du snapshot`() = runTest {
        viewModel.onCustomRangeSelected("2026-05-01", "2026-05-31")
        advanceUntilIdle()

        val snapshot = FakeDashboardRepository.fakeSnapshot()
        fakeRepo.emitCustomSnapshot("2026-05-01", "2026-05-31", snapshot)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val point = state.snapshot?.chart?.firstOrNull()
        assertNotNull(point)
        assertEquals(1, point?.newCustomers)
    }
}
