package com.rebuildit.prestaflow.ui.settings

import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import com.rebuildit.prestaflow.fakes.FakeOrdersPreferencesRepository
import com.rebuildit.prestaflow.fakes.FakeOrdersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [SwipePrefsViewModel] — focus sur le chargement des statuts
 * (succès, erreur, réessai) pour l'écran de configuration du swipe.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwipePrefsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePrefs: FakeOrdersPreferencesRepository
    private lateinit var fakeOrders: FakeOrdersRepository

    private val sampleStatuses =
        listOf(
            OrderStatusFilter(id = 2, name = "Paiement accepté", color = "#00ff00"),
            OrderStatusFilter(id = 4, name = "Expédié", color = "#0000ff"),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePrefs = FakeOrdersPreferencesRepository()
        fakeOrders = FakeOrdersRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `chargement réussi peuple les statuts sans erreur`() =
        runTest {
            fakeOrders.orderStatuses = sampleStatuses
            val viewModel = SwipePrefsViewModel(fakePrefs, fakeOrders)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(sampleStatuses, state.availableStatuses)
            assertFalse(state.statusesError)
            assertFalse(state.isLoadingStatuses)
        }

    @Test
    fun `échec du chargement passe statusesError à true et laisse la liste vide`() =
        runTest {
            fakeOrders.shouldThrowOnGetStatuses = true
            val viewModel = SwipePrefsViewModel(fakePrefs, fakeOrders)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.statusesError)
            assertTrue(state.availableStatuses.isEmpty())
            assertFalse(state.isLoadingStatuses)
        }

    @Test
    fun `réessai après échec recharge les statuts et efface l'erreur`() =
        runTest {
            fakeOrders.shouldThrowOnGetStatuses = true
            val viewModel = SwipePrefsViewModel(fakePrefs, fakeOrders)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.statusesError)

            // Le réseau revient : le bouton Réessayer appelle loadStatuses().
            fakeOrders.shouldThrowOnGetStatuses = false
            fakeOrders.orderStatuses = sampleStatuses
            viewModel.loadStatuses()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.statusesError)
            assertEquals(sampleStatuses, state.availableStatuses)
        }
}
