package com.rebuildit.prestaflow.ui.root

import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeCapabilitiesRepository
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
import org.junit.Before
import org.junit.Test

/**
 * Tests unitaires du [RootViewModel].
 *
 * Points vérifiés :
 * - Les capacités sont rafraîchies dès qu'une session authentifiée est observée (démarrage à chaud
 *   avec une boutique déjà active, ET après un changement de boutique — nouveau token).
 * - Pas de rafraîchissement tant que l'état reste non authentifié.
 * - [RootViewModel.unreadSavCount] reflète le flux exposé par [com.rebuildit.prestaflow.domain.sav.SavRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeCapabilitiesRepo: FakeCapabilitiesRepository
    private lateinit var fakeSavRepo: FakeSavRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepo = FakeAuthRepository()
        fakeCapabilitiesRepo = FakeCapabilitiesRepository()
        fakeSavRepo = FakeSavRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() =
        RootViewModel(
            authRepository = fakeAuthRepo,
            capabilitiesRepository = fakeCapabilitiesRepo,
            savRepository = fakeSavRepo,
        )

    @Test
    fun `un refresh des capacites est declenche des la construction si deja authentifie`() =
        runTest(testDispatcher) {
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeCapabilitiesRepo.refreshCallCount)
        }

    @Test
    fun `un changement de boutique active redeclenche un refresh des capacites`() =
        runTest(testDispatcher) {
            buildViewModel()
            advanceUntilIdle()
            assertEquals(1, fakeCapabilitiesRepo.refreshCallCount)

            // Nouveau token (donc un AuthState.Authenticated structurellement différent, sans quoi
            // StateFlow n'émettrait rien) = nouvelle boutique active, cf. AuthRepositoryImpl.activate().
            fakeAuthRepo.emitAuthState(AuthState.Authenticated(FakeAuthRepository.fakeToken().copy(value = "fake-token-2")))
            advanceUntilIdle()

            assertEquals(2, fakeCapabilitiesRepo.refreshCallCount)
        }

    @Test
    fun `aucun refresh n est declenche si l etat reste non authentifie`() =
        runTest(testDispatcher) {
            fakeAuthRepo.emitAuthState(AuthState.Unauthenticated)
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeCapabilitiesRepo.refreshCallCount)
        }

    @Test
    fun `unreadSavCount reflete le flux expose par SavRepository`() =
        runTest(testDispatcher) {
            fakeSavRepo.emitUnreadCount(97)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(97, viewModel.unreadSavCount.value)
        }
}
