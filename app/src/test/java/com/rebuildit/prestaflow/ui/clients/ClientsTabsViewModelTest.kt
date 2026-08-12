package com.rebuildit.prestaflow.ui.clients

import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.model.AuthScopes
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.fakes.FakeAuthRepository
import com.rebuildit.prestaflow.fakes.FakeCapabilitiesRepository
import com.rebuildit.prestaflow.fakes.FakeReviewsRepository
import com.rebuildit.prestaflow.fakes.FakeSavRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ClientsTabsViewModel] n'est qu'un passe-plat vers [com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository]
 * pour les capacités, vers [com.rebuildit.prestaflow.domain.auth.AuthRepository] pour les scopes du
 * jeton actif, et vers [com.rebuildit.prestaflow.domain.sav.SavRepository] /
 * [com.rebuildit.prestaflow.domain.reviews.ReviewsRepository] pour les compteurs affichés sur les
 * sous-onglets SAV et Avis (répartition du chiffre agrégé de la pastille du shell, cf.
 * [com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientsTabsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        capabilities: ShopCapabilities = ShopCapabilities(),
        authRepository: FakeAuthRepository = FakeAuthRepository(),
        savRepository: FakeSavRepository = FakeSavRepository(),
        reviewsRepository: FakeReviewsRepository = FakeReviewsRepository(),
    ) = ClientsTabsViewModel(
        authRepository = authRepository,
        capabilitiesRepository = FakeCapabilitiesRepository(initial = capabilities),
        savRepository = savRepository,
        reviewsRepository = reviewsRepository,
    )

    @Test
    fun `expose la valeur courante du CapabilitiesRepository`() =
        runTest(testDispatcher) {
            val fakeRepo = FakeCapabilitiesRepository(initial = ShopCapabilities(reviews = true))

            val viewModel =
                ClientsTabsViewModel(
                    authRepository = FakeAuthRepository(),
                    capabilitiesRepository = fakeRepo,
                    savRepository = FakeSavRepository(),
                    reviewsRepository = FakeReviewsRepository(),
                )

            assertTrue(viewModel.capabilities.value.reviews)
        }

    @Test
    fun `reflete les emissions ulterieures du repository`() =
        runTest(testDispatcher) {
            val fakeRepo = FakeCapabilitiesRepository(initial = ShopCapabilities(reviews = false))
            val viewModel =
                ClientsTabsViewModel(
                    authRepository = FakeAuthRepository(),
                    capabilitiesRepository = fakeRepo,
                    savRepository = FakeSavRepository(),
                    reviewsRepository = FakeReviewsRepository(),
                )
            assertFalse(viewModel.capabilities.value.reviews)

            fakeRepo.emit(ShopCapabilities(reviews = true))

            assertEquals(fakeRepo.capabilities.value, viewModel.capabilities.value)
            assertTrue(viewModel.capabilities.value.reviews)
        }

    @Test
    fun `expose le compteur SAV a traiter du SavRepository`() =
        runTest(testDispatcher) {
            val fakeSavRepo = FakeSavRepository(initialToProcessCount = 2)

            val viewModel = buildViewModel(savRepository = fakeSavRepo)

            assertEquals(2, viewModel.savToProcessCount.first())
        }

    @Test
    fun `expose le compteur avis en attente du ReviewsRepository`() =
        runTest(testDispatcher) {
            val fakeReviewsRepo = FakeReviewsRepository(initialPendingCount = 3)

            val viewModel = buildViewModel(reviewsRepository = fakeReviewsRepo)

            assertEquals(3, viewModel.pendingReviewCount.first())
        }

    // ─── scopes — cf. défaut vécu par Greg (403 sur le SAV sans le scope) ──────

    @Test
    fun `expose les scopes du jeton actif des la construction, sans attendre de collecte`() =
        runTest(testDispatcher) {
            val fakeAuthRepo =
                FakeAuthRepository().apply {
                    emitAuthState(AuthState.Authenticated(FakeAuthRepository.fakeToken(scopes = listOf(AuthScopes.SAV_READ))))
                }

            val viewModel = buildViewModel(authRepository = fakeAuthRepo)

            // Valeur SYNCHRONE, avant tout advanceUntilIdle() : une première composition ne doit
            // jamais voir un onglet SAV/Avis incohérent avec le jeton réellement actif.
            assertEquals(setOf(AuthScopes.SAV_READ), viewModel.scopes.value)
        }

    @Test
    fun `scopes ne porte ni sav read ni reviews moderate avec le jeton par defaut`() =
        runTest(testDispatcher) {
            // Jeton par défaut de FakeAuthRepository : d'autres scopes (orders, products,
            // customers), mais ni sav.read ni reviews.moderate — cf. sa Javadoc.
            val viewModel = buildViewModel()

            assertFalse(AuthScopes.SAV_READ in viewModel.scopes.value)
            assertFalse(AuthScopes.REVIEWS_MODERATE in viewModel.scopes.value)
        }

    @Test
    fun `scopes reflete un changement de boutique active (nouveau jeton)`() =
        runTest(testDispatcher) {
            val fakeAuthRepo = FakeAuthRepository()
            val viewModel = buildViewModel(authRepository = fakeAuthRepo)
            assertFalse(AuthScopes.REVIEWS_MODERATE in viewModel.scopes.value)

            fakeAuthRepo.emitAuthState(
                AuthState.Authenticated(FakeAuthRepository.fakeToken(scopes = listOf(AuthScopes.REVIEWS_MODERATE))),
            )
            advanceUntilIdle()

            assertEquals(setOf(AuthScopes.REVIEWS_MODERATE), viewModel.scopes.value)
        }
}
