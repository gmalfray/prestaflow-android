package com.rebuildit.prestaflow.ui.root

import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.model.AuthScopes
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
 * - Le compteur SAV n'est rafraîchi QUE si le jeton porte `sav.read` ; le compteur Avis QUE si la
 *   capacité `reviews` est vraie ET que le jeton porte `reviews.moderate` — capacité ≠ droit (cf.
 *   défaut vécu par Greg : jeton sans `sav.read` sur une boutique où le SAV est nativement
 *   disponible, la route connecteur répondrait 403 sans cette garde).
 * - [RootViewModel.clientsBadgeCount] est la somme SAV + Avis, chaque part n'étant comptée que si
 *   sa capacité ET son scope sont réunis.
 *
 * Le token par défaut de [FakeAuthRepository.fakeToken] NE porte NI `sav.read` NI
 * `reviews.moderate` (cf. sa Javadoc) : c'est délibéré, pour que tout test qui a besoin de l'un ou
 * l'autre soit obligé de le demander explicitement — exactement le garde-fou qui manquait avant ce
 * correctif.
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

    /** Jeton portant les deux scopes secondaires — baseline des tests qui ne portent pas sur eux. */
    private fun authenticateWithBothScopes() {
        fakeAuthRepo.emitAuthState(
            AuthState.Authenticated(FakeAuthRepository.fakeToken(scopes = listOf(AuthScopes.SAV_READ, AuthScopes.REVIEWS_MODERATE))),
        )
    }

    @Test
    fun `un refresh des capacites est declenche des la construction si deja authentifie`() =
        runTest(testDispatcher) {
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeCapabilitiesRepo.refreshCallCount)
        }

    @Test
    fun `un refresh du compteur SAV a traiter est declenche si le jeton porte sav read`() =
        runTest(testDispatcher) {
            authenticateWithBothScopes()
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeSavRepo.refreshToProcessCountCallCount)
        }

    @Test
    fun `aucun refresh du compteur SAV n est declenche si le jeton ne porte pas sav read`() =
        runTest(testDispatcher) {
            // Jeton par défaut : ni sav.read ni reviews.moderate (cf. Javadoc de la classe).
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeSavRepo.refreshToProcessCountCallCount)
        }

    @Test
    fun `un refresh du compteur avis est declenche si la capacite reviews est vraie et le scope present`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = true)
            authenticateWithBothScopes()
            buildViewModel()
            advanceUntilIdle()

            assertEquals(1, fakeReviewsRepo.refreshPendingCountCallCount)
        }

    @Test
    fun `aucun refresh du compteur avis n est declenche si la capacite reviews est fausse`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = false)
            authenticateWithBothScopes()
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeReviewsRepo.refreshPendingCountCallCount)
        }

    @Test
    fun `aucun refresh du compteur avis n est declenche si le jeton ne porte pas reviews moderate meme si la capacite est vraie`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = true)
            // Jeton par défaut : pas de reviews.moderate.
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeReviewsRepo.refreshPendingCountCallCount)
        }

    @Test
    fun `un changement de boutique active redeclenche un refresh des capacites`() =
        runTest(testDispatcher) {
            authenticateWithBothScopes()
            buildViewModel()
            advanceUntilIdle()
            assertEquals(1, fakeCapabilitiesRepo.refreshCallCount)

            // Nouveau token (donc un AuthState.Authenticated structurellement différent, sans quoi
            // StateFlow n'émettrait rien) = nouvelle boutique active, cf. AuthRepositoryImpl.activate().
            fakeAuthRepo.emitAuthState(
                AuthState.Authenticated(
                    FakeAuthRepository.fakeToken(scopes = listOf(AuthScopes.SAV_READ, AuthScopes.REVIEWS_MODERATE))
                        .copy(value = "fake-token-2"),
                ),
            )
            advanceUntilIdle()

            assertEquals(2, fakeCapabilitiesRepo.refreshCallCount)
            assertEquals(2, fakeSavRepo.refreshToProcessCountCallCount)
        }

    @Test
    fun `aucun refresh n est declenche si l etat reste non authentifie`() =
        runTest(testDispatcher) {
            fakeAuthRepo.emitAuthState(AuthState.Unauthenticated)
            buildViewModel()
            advanceUntilIdle()

            assertEquals(0, fakeCapabilitiesRepo.refreshCallCount)
            assertEquals(0, fakeSavRepo.refreshToProcessCountCallCount)
            assertEquals(0, fakeReviewsRepo.refreshPendingCountCallCount)
        }

    @Test
    fun `clientsBadgeCount est la somme SAV + avis quand capacite et scopes sont reunis`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = true)
            authenticateWithBothScopes()
            fakeSavRepo.emitToProcessCount(97)
            fakeReviewsRepo.emitPendingCount(3)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(100, viewModel.clientsBadgeCount.value)
        }

    @Test
    fun `clientsBadgeCount ignore le compteur avis quand la capacite reviews est fausse`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = false)
            authenticateWithBothScopes()
            fakeSavRepo.emitToProcessCount(97)
            // Avis non nul malgré tout (ex. valeur résiduelle d'une capacité précédemment vraie) :
            // ne doit surtout pas fuiter dans la somme tant que la capacité est fausse — sinon on
            // recrée exactement le défaut initial (un compteur qui annonce un avis invisible).
            fakeReviewsRepo.emitPendingCount(3)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(97, viewModel.clientsBadgeCount.value)
        }

    @Test
    fun `clientsBadgeCount ignore le compteur SAV quand le jeton ne porte pas sav read`() =
        runTest(testDispatcher) {
            // Jeton par défaut sans sav.read, mais le compteur SAV est déjà non nul (ex. valeur
            // résiduelle d'une session précédente où le scope était présent) : ne doit surtout pas
            // fuiter dans la somme — c'est exactement le défaut vécu par Greg.
            fakeSavRepo.emitToProcessCount(88)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(0, viewModel.clientsBadgeCount.value)
        }

    @Test
    fun `clientsBadgeCount ignore le compteur avis quand le jeton ne porte pas reviews moderate meme si la capacite est vraie`() =
        runTest(testDispatcher) {
            fakeCapabilitiesRepo.nextRefreshResult = ShopCapabilities(sav = true, reviews = true)
            // Jeton par défaut sans reviews.moderate.
            fakeReviewsRepo.emitPendingCount(3)
            val viewModel = buildViewModel()
            advanceUntilIdle()

            assertEquals(0, viewModel.clientsBadgeCount.value)
        }
}
