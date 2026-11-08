package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardPreferencesRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardRepository
import com.rebuildit.prestaflow.fakes.FakeDashboardWidgetsRepository
import com.rebuildit.prestaflow.fakes.FakeTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelWidgetsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val widgets = FakeDashboardWidgetsRepository()
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
            dashboardRepository = FakeDashboardRepository(),
            dashboardPrefsRepository = prefs,
            widgetsRepository = widgets,
            networkErrorMapper = NetworkErrorMapper(),
            authRepository = FakeAuthRepository(),
            resumeRefreshGuard = ScreenResumeRefreshGuard(FakeTimeProvider()),
        )

    private fun layoutWith(vararg visible: DashboardWidget) =
        DashboardLayout(
            DashboardLayout.DEFAULT.items.map { it.copy(visible = it.widget in visible) },
        )

    @Test
    fun `widgets masqués par défaut - aucun appel réseau`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(0, widgets.latestOrdersCalls + widgets.cartsCalls + widgets.reviewsCalls + widgets.topProductsCalls)
            assertEquals(WidgetLoad.Idle, vm.uiState.value.latestOrders)
        }

    @Test
    fun `un widget rendu visible charge uniquement ses données`() =
        runTest {
            val vm = vm()
            advanceUntilIdle()
            prefs.setWidgetLayout(layoutWith(DashboardWidget.CARTS))
            advanceUntilIdle()
            assertEquals(1, widgets.cartsCalls)
            assertEquals(0, widgets.latestOrdersCalls)
            assertTrue(vm.uiState.value.abandonedCarts is WidgetLoad.Data)
        }

    @Test
    fun `échec d'un widget - état local en erreur, les autres intacts`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.LATEST_ORDERS))
            widgets.failure = RuntimeException("boom")
            val vm = vm()
            advanceUntilIdle()
            assertEquals(WidgetLoad.Error, vm.uiState.value.latestOrders)
            assertEquals(null, vm.uiState.value.error)

            widgets.failure = null
            vm.onWidgetRetry(DashboardWidget.LATEST_ORDERS)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.latestOrders is WidgetLoad.Data)
        }

    @Test
    fun `avis - module absent donne Unavailable sans erreur globale`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.REVIEWS))
            widgets.reviews = null
            val vm = vm()
            advanceUntilIdle()
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.pendingReviews)
            assertEquals(null, vm.uiState.value.error)

            widgets.reviews = PendingReviewsCount(3, hasMore = false)
            vm.onRefresh()
            advanceUntilIdle()
            assertEquals(WidgetLoad.Data(PendingReviewsCount(3, false)), vm.uiState.value.pendingReviews)
        }

    @Test
    fun `top produits - suit la période sélectionnée`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.TOP_PRODUCTS))
            widgets.topProducts = listOf(TopProduct(1, "A", 2, 10.0))
            val vm = vm()
            advanceUntilIdle()
            val callsBefore = widgets.topProductsCalls
            vm.onPeriodSelected(DashboardPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(callsBefore + 1, widgets.topProductsCalls)
            assertTrue(widgets.lastTopProductsRange!!.first!!.endsWith("-01-01 00:00:00"))
            assertTrue(widgets.lastTopProductsRange!!.second!!.endsWith(" 23:59:59"))
        }
}
