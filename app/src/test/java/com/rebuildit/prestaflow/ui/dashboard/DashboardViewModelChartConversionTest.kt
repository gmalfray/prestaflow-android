package com.rebuildit.prestaflow.ui.dashboard

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardChartMode
import com.rebuildit.prestaflow.domain.dashboard.model.FreeConversion
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardPreferencesRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardWidgetsRepository
import com.rebuildit.prestaflow.fakes.FakeTimeProvider
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelChartConversionTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repo = FakeDashboardRepository()
    private val prefs = FakeDashboardPreferencesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() =
        DashboardViewModel(
            dashboardRepository = repo,
            dashboardPrefsRepository = prefs,
            widgetsRepository = FakeDashboardWidgetsRepository(),
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = FakeAuthRepository(),
            resumeRefreshGuard = ScreenResumeRefreshGuard(FakeTimeProvider()),
        )

    @Test
    fun `courbe par défaut = CA vs N-1`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(DashboardChartMode.TURNOVER_VS_PREVIOUS, vm.uiState.value.chartMode)
        }

    @Test
    fun `courbe persistée relue à l'ouverture`() =
        runTest {
            prefs.setChartMode(DashboardChartMode.AVG_CART)
            val vm = vm()
            advanceUntilIdle()
            assertEquals(DashboardChartMode.AVG_CART, vm.uiState.value.chartMode)
        }

    @Test
    fun `onChartModeSelected met à jour l'état et persiste`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            vm.uiState.test {
                awaitItem()
                vm.onChartModeSelected(DashboardChartMode.PAID_VS_FREE)
                advanceUntilIdle()
                assertEquals(DashboardChartMode.PAID_VS_FREE, expectMostRecentItem().chartMode)
            }
            assertEquals(DashboardChartMode.PAID_VS_FREE, prefs.chartMode.first())
        }

    @Test
    fun `conversion chargée dans l'état`() =
        runTest {
            repo.freeConversion = FreeConversion(freeFirstCustomers = 40, paidFirstCustomers = 100, converted = 10, rate = 25.0)
            val vm = vm()
            advanceUntilIdle()
            val state = vm.uiState.value.conversion
            assertTrue(state is ConversionState.Data)
            assertEquals(25.0, (state as ConversionState.Data).conversion.rate, 0.0)
        }

    @Test
    fun `échec de la conversion - carte en erreur, dashboard non impacté`() =
        runTest {
            repo.freeConversionException = IOException("boom")
            val vm = vm()
            advanceUntilIdle()
            assertEquals(ConversionState.Error, vm.uiState.value.conversion)
            assertEquals(null, vm.uiState.value.error)
        }
}
