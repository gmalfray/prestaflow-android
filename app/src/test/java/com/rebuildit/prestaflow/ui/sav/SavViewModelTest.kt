package com.rebuildit.prestaflow.ui.sav

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.domain.sav.model.SavThread
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.domain.sav.model.SavThreadsPage
import com.rebuildit.prestaflow.fakes.FakeSavRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeSavRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeSavRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = SavViewModel(fakeRepo, NetworkErrorMapper())

    @Test
    fun `charge la premiere page sans filtre au demarrage`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = listOf(buildThread(1L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value as SavUiState.Content
            assertEquals(1, state.threads.size)
            assertEquals(SavStatusFilter.ALL, state.filter)
            assertNull(fakeRepo.lastFetchThreadsCall?.status)
        }

    @Test
    fun `etat Error si le premier chargement echoue`() =
        runTest {
            fakeRepo.shouldThrowOnFetchThreads = true

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is SavUiState.Error)
        }

    @Test
    fun `onFilterChange recharge avec le statut correspondant`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = emptyList(), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onFilterChange(SavStatusFilter.CLOSED)
            advanceUntilIdle()

            assertEquals(SavThreadStatus.CLOSED, fakeRepo.lastFetchThreadsCall?.status)
            val state = vm.uiState.value as SavUiState.Content
            assertEquals(SavStatusFilter.CLOSED, state.filter)
        }

    @Test
    fun `onFilterChange vers le meme filtre ne redeclenche rien`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = emptyList(), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeRepo.lastFetchThreadsCall = null

            vm.onFilterChange(SavStatusFilter.ALL)
            advanceUntilIdle()

            assertEquals(null, fakeRepo.lastFetchThreadsCall)
        }

    @Test
    fun `onLoadMore accumule les fils sans effacer la page precedente`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = listOf(buildThread(1L)), hasNext = true, nextOffset = 20)
            val vm = buildViewModel()
            advanceUntilIdle()

            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = listOf(buildThread(2L)), hasNext = false, nextOffset = 40)
            vm.onLoadMore()
            advanceUntilIdle()

            val state = vm.uiState.value as SavUiState.Content
            assertEquals(listOf(1L, 2L), state.threads.map { it.id })
            assertEquals(20, fakeRepo.lastFetchThreadsCall?.offset)
        }

    @Test
    fun `onLoadMore ignore si hasNextPage est faux`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = listOf(buildThread(1L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeRepo.lastFetchThreadsCall = null

            vm.onLoadMore()
            advanceUntilIdle()

            assertEquals(null, fakeRepo.lastFetchThreadsCall)
        }

    @Test
    fun `onRefresh conserve le contenu existant en cas d echec et expose l erreur`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = listOf(buildThread(1L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            fakeRepo.shouldThrowOnFetchThreads = true
            vm.onRefresh()
            advanceUntilIdle()

            val state = vm.uiState.value as SavUiState.Content
            assertEquals(1, state.threads.size)
            assertTrue("Une erreur doit être exposée sans perdre le contenu", state.error != null)
        }

    @Test
    fun `onRefresh emet isRefreshing pendant le chargement`() =
        runTest {
            fakeRepo.fetchThreadsResult = SavThreadsPage(threads = listOf(buildThread(1L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.uiState.test {
                val initial = expectMostRecentItem() as SavUiState.Content
                assertEquals(false, initial.isRefreshing)

                vm.onRefresh()

                val refreshing = awaitItem() as SavUiState.Content
                assertTrue(refreshing.isRefreshing)

                val done = awaitItem() as SavUiState.Content
                assertEquals(false, done.isRefreshing)

                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun buildThread(id: Long) =
        SavThread(
            id = id,
            status = SavThreadStatus.OPEN,
            unread = false,
            toProcess = false,
            customerId = 1L,
            customerName = "Client Test",
            customerEmail = "client@example.com",
            orderId = null,
            orderReference = null,
            lastMessageAtIso = "2026-08-01 10:00:00",
            dateAddedIso = "2026-08-01 10:00:00",
            dateUpdatedIso = "2026-08-01 10:00:00",
        )
}
