package com.rebuildit.prestaflow.ui.settings

import com.rebuildit.prestaflow.domain.language.AppLanguage
import com.rebuildit.prestaflow.fakes.FakeLanguageRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires de [LanguageViewModel] — sélecteur de langue in-app (Réglages) : observation du
 * tag courant exposé par [com.rebuildit.prestaflow.domain.language.LanguageRepository] et relais de
 * [LanguageViewModel.setLanguage].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeLanguageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeLanguageRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sans langue forcee expose le mode Systeme (null)`() =
        runTest {
            val viewModel = LanguageViewModel(fakeRepo)
            backgroundScope.launch { viewModel.currentLanguage.collect {} }
            advanceUntilIdle()

            assertNull(viewModel.currentLanguage.value)
        }

    @Test
    fun `une langue deja forcee cote repository est exposee au demarrage`() =
        runTest {
            fakeRepo = FakeLanguageRepository(initial = "de")
            val viewModel = LanguageViewModel(fakeRepo)
            backgroundScope.launch { viewModel.currentLanguage.collect {} }
            advanceUntilIdle()

            assertEquals(AppLanguage.GERMAN, viewModel.currentLanguage.value)
        }

    @Test
    fun `setLanguage relaie le tag choisi au repository et republie la valeur`() =
        runTest {
            val viewModel = LanguageViewModel(fakeRepo)
            backgroundScope.launch { viewModel.currentLanguage.collect {} }
            advanceUntilIdle()

            viewModel.setLanguage(AppLanguage.ITALIAN)
            advanceUntilIdle()

            assertEquals("it", fakeRepo.lastSetTag)
            assertEquals(AppLanguage.ITALIAN, viewModel.currentLanguage.value)
        }

    @Test
    fun `setLanguage null revient au mode Systeme`() =
        runTest {
            fakeRepo = FakeLanguageRepository(initial = "es")
            val viewModel = LanguageViewModel(fakeRepo)
            backgroundScope.launch { viewModel.currentLanguage.collect {} }
            advanceUntilIdle()

            viewModel.setLanguage(null)
            advanceUntilIdle()

            assertNull(fakeRepo.lastSetTag)
            assertNull(viewModel.currentLanguage.value)
        }

    @Test
    fun `un changement externe du tag se repercute sur le flux observe`() =
        runTest {
            val viewModel = LanguageViewModel(fakeRepo)
            backgroundScope.launch { viewModel.currentLanguage.collect {} }
            advanceUntilIdle()

            fakeRepo.emit("pt")
            advanceUntilIdle()

            assertEquals(AppLanguage.PORTUGUESE, viewModel.currentLanguage.value)
        }
}
