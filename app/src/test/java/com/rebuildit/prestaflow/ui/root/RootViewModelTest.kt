package com.rebuildit.prestaflow.ui.root

import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeCapabilitiesRepository
import com.rebuildit.prestaflow.fakes.FakeReviewsRepository
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
 * - Le compteur SAV est TOUJOURS rafraîchi (natif, capacité toujours vraie) ; le compteur Avis ne
 *   l'est QUE si la capacité `reviews` est vraie (sinon la route connecteur répondrait 409).
 * - [RootViewModel.clientsBadgeCount] est la somme SAV + Avis, la part avis n'étant comptée que si
 *   `reviews` est vrai (cf. défaut remonté : la pastille du shell ne doit jamais compter un avis
 *   invisible sur une boutique sans le module).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeAuthRepo: FakeAuthRepository
    private lateinit var fakeCapabilitiesRepo: FakeCapabilitiesRepository
    private lateinit var fakeSavRepo: FakeSavRepository
    private lateinit var fakeReviewsRepo: FakeReviewsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepo = FakeAuthRepository()
        fakeCapabilitiesRepo = FakeCapabilitiesRepository()
        fakeSavRepo = FakeSavRepository()
        fakeReviewsRepo = FakeReviewsRepository()
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
            reviewsRepository = fakeReviewsRepo,
        )

    @Test
    fun `un refresh des capacites est declenche des la construction si deja authentifie`() =
        runTest(testDispatcher) {
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeCapabilitiesRepo.refreshCallCount)
        }

    @Test
    fun `un refresh du compteur SAV non lus est declenche des la construction si deja authentifie`() =
        runTest(testDispatcher) {
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeSavRepo.refreshUnreadCountCallCount)
        }

    @Test
    fun `un refresh du compteur avis est declenche si la capacite reviews est vraie`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = true)
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeReviewsRepo.refreshPendingCountCallCount)
        }

    @Test
    fun `aucun refresh du compteur avis n est declenche si la capacite reviews est fausse`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = false)
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeReviewsRepo.refreshPendingCountCallCount)
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
            assertEquals(2, fakeSavRepo.refreshUnreadCountCallCount)
        }

    @Test
    fun `aucun refresh n est declenche si l etat reste non authentifie`() =
        runTest(testDispatcher) {
            fakeAuthRepo.emitAuthState(AuthState.Unauthenticated)
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeCapabilitiesRepo.refreshCallCount)
            assertEquals(0, fakeSavRepo.refreshUnreadCountCallCount)
            assertEquals(0, fakeReviewsRepo.refreshPendingCountCallCount)
        }

    @Test
    fun `clientsBadgeCount est la somme SAV + avis quand la capacite reviews est vraie`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = true)
            fakeSavRepo.emitUnreadCount(97)
            fakeReviewsRepo.emitPendingCount(3)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(100, viewModel.clientsBadgeCount.value)
        }

    @Test
    fun `clientsBadgeCount ignore le compteur avis quand la capacite reviews est fausse`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = false)
            fakeSavRepo.emitUnreadCount(97)
            // Avis non nul malgré tout (ex. valeur résiduelle d'une capacité précédemment vraie) :
            // ne doit surtout pas fuiter dans la somme tant que la capacité est fausse — sinon on
            // recrée exactement le défaut initial (un compteur qui annonce un avis invisible).
            fakeReviewsRepo.emitPendingCount(3)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(97, viewModel.clientsBadgeCount.value)
        }
}
