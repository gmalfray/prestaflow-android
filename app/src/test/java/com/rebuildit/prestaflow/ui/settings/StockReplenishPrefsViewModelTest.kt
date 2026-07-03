package com.rebuildit.prestaflow.ui.settings

import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.fakes.FakeStockReplenishPreferencesRepository
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
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [StockReplenishPrefsViewModel] — section « Réappro / boutons rapides » des
 * Réglages (Lot 2) : observation + persistance des montants configurés.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StockReplenishPrefsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeStockReplenishPreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeStockReplenishPreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sans preference enregistree expose le defaut Lot 1`() =
        runTest {
            val viewModel = StockReplenishPrefsViewModel(fakeRepo)
            backgroundScope.launch { viewModel.quickAddAmounts.collect {} }
            advanceUntilIdle()

            assertEquals(DEFAULT_QUICK_ADD_AMOUNTS, viewModel.quickAddAmounts.value)
        }

    @Test
    fun `setQuickAddAmounts persiste et republie la nouvelle valeur`() =
        runTest {
            val viewModel = StockReplenishPrefsViewModel(fakeRepo)
            backgroundScope.launch { viewModel.quickAddAmounts.collect {} }
            advanceUntilIdle()

            viewModel.setQuickAddAmounts(listOf(1, 5, 10, 20, 50))
            advanceUntilIdle()

            assertEquals(listOf(1, 5, 10, 20, 50), viewModel.quickAddAmounts.value)
            assertEquals(listOf(1, 5, 10, 20, 50), fakeRepo.stored)
        }

    @Test
    fun `une valeur invalide est normalisee avant persistance`() =
        runTest {
            val viewModel = StockReplenishPrefsViewModel(fakeRepo)
            backgroundScope.launch { viewModel.quickAddAmounts.collect {} }
            advanceUntilIdle()

            // 6 boutons + un zéro invalide : bornage à 5 boutons, zéro filtré (cf. normalizeQuickAddAmounts).
            viewModel.setQuickAddAmounts(listOf(1, 2, 0, 3, 4, 5, 6))
            advanceUntilIdle()

            assertEquals(listOf(1, 2, 3, 4, 5), viewModel.quickAddAmounts.value)
        }

    @Test
    fun `un changement externe des preferences se repercute sur le flux observe`() =
        runTest {
            val viewModel = StockReplenishPrefsViewModel(fakeRepo)
            backgroundScope.launch { viewModel.quickAddAmounts.collect {} }
            advanceUntilIdle()

            fakeRepo.emit(listOf(2, 4))
            advanceUntilIdle()

            assertEquals(listOf(2, 4), viewModel.quickAddAmounts.value)
        }

    // ─── Son au scan (Lot 3) ──────────────────────────────────────────────────

    @Test
    fun `sans preference enregistree le son au scan est active par defaut`() =
        runTest {
            val viewModel = StockReplenishPrefsViewModel(fakeRepo)
            backgroundScope.launch { viewModel.soundOnScan.collect {} }
            advanceUntilIdle()

            assertEquals(true, viewModel.soundOnScan.value)
        }

    @Test
    fun `setSoundOnScan persiste et republie la nouvelle valeur`() =
        runTest {
            val viewModel = StockReplenishPrefsViewModel(fakeRepo)
            backgroundScope.launch { viewModel.soundOnScan.collect {} }
            advanceUntilIdle()

            viewModel.setSoundOnScan(false)
            advanceUntilIdle()

            assertEquals(false, viewModel.soundOnScan.value)
            assertEquals(false, fakeRepo.storedSoundOnScan)
        }
}
