package com.rebuildit.prestaflow.ui.dashboard

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardLayout
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardPeriod
import com.rebuildit.prestaflow.domain.dashboard.model.DashboardWidget
import com.rebuildit.prestaflow.domain.dashboard.model.PendingReviewsCount
import com.rebuildit.prestaflow.domain.dashboard.model.SalesByUniverse
import com.rebuildit.prestaflow.domain.dashboard.model.TopProduct
import com.rebuildit.prestaflow.domain.dashboard.model.UniverseSales
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

    @Test
    fun `ventes par univers - chargé seulement si visible puis rechargé au changement de période`() =
        runTest {
            val hidden = vm()
            advanceUntilIdle()
            assertEquals(0, widgets.salesByUniverseCalls)
            assertEquals(WidgetLoad.Idle, hidden.uiState.value.salesByUniverse)

            prefs.setWidgetLayout(layoutWith(DashboardWidget.SALES_BY_UNIVERSE))
            widgets.salesByUniverse = SalesByUniverse(listOf(UniverseSales("kits", false, 10.0, 1)), 2, 0f)
            val vm = vm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.salesByUniverse is WidgetLoad.Data)
            val callsBefore = widgets.salesByUniverseCalls
            vm.onPeriodSelected(DashboardPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(callsBefore + 1, widgets.salesByUniverseCalls)
            assertTrue(widgets.lastSalesByUniverseRange!!.first!!.endsWith("-01-01 00:00:00"))
        }

    @Test
    fun `ventes par univers - endpoint absent donne Unavailable, échec donne Error`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.SALES_BY_UNIVERSE))
            widgets.salesByUniverse = null
            val vm = vm()
            advanceUntilIdle()
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.salesByUniverse)

            widgets.failure = IllegalStateException("boom")
            vm.onWidgetRetry(DashboardWidget.SALES_BY_UNIVERSE)
            advanceUntilIdle()
            assertEquals(WidgetLoad.Error, vm.uiState.value.salesByUniverse)
        }

    @Test
    fun `fidélité et canaux - masqués par défaut, rechargés au changement de période`() =
        runTest {
            val hidden = vm()
            advanceUntilIdle()
            assertEquals(0, widgets.customerRetentionCalls)
            assertEquals(0, widgets.acquisitionChannelsCalls)
            assertEquals(WidgetLoad.Idle, hidden.uiState.value.customerRetention)

            prefs.setWidgetLayout(layoutWith(DashboardWidget.CUSTOMER_RETENTION, DashboardWidget.ACQUISITION_CHANNELS))
            val vm = vm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.customerRetention is WidgetLoad.Data)
            assertTrue(vm.uiState.value.acquisitionChannels is WidgetLoad.Data)
            assertEquals(30, widgets.lastChannelsWindowDays)
            val retentionBefore = widgets.customerRetentionCalls
            val channelsBefore = widgets.acquisitionChannelsCalls
            vm.onPeriodSelected(DashboardPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(retentionBefore + 1, widgets.customerRetentionCalls)
            assertEquals(channelsBefore + 1, widgets.acquisitionChannelsCalls)
            assertTrue(widgets.lastRetentionRange!!.first!!.endsWith("-01-01 00:00:00"))
        }

    @Test
    fun `cohortes - 12 mois fixes, pas rechargées au changement de période`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.FREE_CONVERSION_COHORTS))
            val vm = vm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.freeCohorts is WidgetLoad.Data)
            assertEquals(12, widgets.lastCohortsMonths)
            val callsBefore = widgets.freeCohortsCalls
            vm.onPeriodSelected(DashboardPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(callsBefore, widgets.freeCohortsCalls)
        }

    @Test
    fun `nouveaux widgets - endpoint absent donne Unavailable, échec donne Error`() =
        runTest {
            prefs.setWidgetLayout(
                layoutWith(
                    DashboardWidget.CUSTOMER_RETENTION,
                    DashboardWidget.ACQUISITION_CHANNELS,
                    DashboardWidget.FREE_CONVERSION_COHORTS,
                ),
            )
            widgets.customerRetention = null
            widgets.acquisitionChannels = null
            widgets.freeCohorts = null
            val vm = vm()
            advanceUntilIdle()
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.customerRetention)
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.acquisitionChannels)
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.freeCohorts)

            widgets.failure = IllegalStateException("boom")
            vm.onWidgetRetry(DashboardWidget.FREE_CONVERSION_COHORTS)
            advanceUntilIdle()
            assertEquals(WidgetLoad.Error, vm.uiState.value.freeCohorts)
        }

    @Test
    fun `saisonnalité - 12 mois fixes, pas rechargée au changement de période`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.MONTHLY_TURNOVER))
            val vm = vm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.monthlyTurnover is WidgetLoad.Data)
            assertEquals(12, widgets.lastMonthlyMonths)
            val callsBefore = widgets.monthlyTurnoverCalls
            vm.onPeriodSelected(DashboardPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(callsBefore, widgets.monthlyTurnoverCalls)
        }

    @Test
    fun `avis et SAV - masqué par défaut, rechargé au changement de période`() =
        runTest {
            val hidden = vm()
            advanceUntilIdle()
            assertEquals(0, widgets.reviewsSummaryCalls)
            assertEquals(0, widgets.monthlyTurnoverCalls)
            assertEquals(WidgetLoad.Idle, hidden.uiState.value.reviewsSummary)

            prefs.setWidgetLayout(layoutWith(DashboardWidget.REVIEWS_SUMMARY))
            val vm = vm()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.reviewsSummary is WidgetLoad.Data)
            val before = widgets.reviewsSummaryCalls
            vm.onPeriodSelected(DashboardPeriod.YEAR)
            advanceUntilIdle()
            assertEquals(before + 1, widgets.reviewsSummaryCalls)
            assertTrue(widgets.lastReviewsSummaryRange!!.first!!.endsWith("-01-01 00:00:00"))
        }

    @Test
    fun `saisonnalité et avis SAV - absent donne Unavailable, échec donne Error`() =
        runTest {
            prefs.setWidgetLayout(layoutWith(DashboardWidget.MONTHLY_TURNOVER, DashboardWidget.REVIEWS_SUMMARY))
            widgets.monthlyTurnover = null
            widgets.reviewsSummary = null
            val vm = vm()
            advanceUntilIdle()
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.monthlyTurnover)
            assertEquals(WidgetLoad.Unavailable, vm.uiState.value.reviewsSummary)

            widgets.failure = IllegalStateException("boom")
            vm.onWidgetRetry(DashboardWidget.REVIEWS_SUMMARY)
            vm.onWidgetRetry(DashboardWidget.MONTHLY_TURNOVER)
            advanceUntilIdle()
            assertEquals(WidgetLoad.Error, vm.uiState.value.reviewsSummary)
            assertEquals(WidgetLoad.Error, vm.uiState.value.monthlyTurnover)
        }
}
