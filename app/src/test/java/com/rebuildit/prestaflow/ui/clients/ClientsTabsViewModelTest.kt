package com.rebuildit.prestaflow.ui.clients

import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.fakes.FakeCapabilitiesRepository
import com.rebuildit.prestaflow.fakes.FakeReviewsRepository
import com.rebuildit.prestaflow.fakes.FakeSavRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ClientsTabsViewModel] n'est qu'un passe-plat vers [com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository]
 * pour les capacités, et vers [com.rebuildit.prestaflow.domain.sav.SavRepository] /
 * [com.rebuildit.prestaflow.domain.reviews.ReviewsRepository] pour les compteurs affichés sur les
 * sous-onglets SAV et Avis (répartition du chiffre agrégé de la pastille du shell, cf.
 * [com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientsTabsViewModelTest {
    private fun buildViewModel(
        capabilities: ShopCapabilities = ShopCapabilities(),
        savRepository: FakeSavRepository = FakeSavRepository(),
        reviewsRepository: FakeReviewsRepository = FakeReviewsRepository(),
    ) = ClientsTabsViewModel(
        capabilitiesRepository = FakeCapabilitiesRepository(initial = capabilities),
        savRepository = savRepository,
        reviewsRepository = reviewsRepository,
    )

    @Test
    fun `expose la valeur courante du CapabilitiesRepository`() {
        val fakeRepo = FakeCapabilitiesRepository(initial = ShopCapabilities(reviews = true))

        val viewModel =
            ClientsTabsViewModel(
                capabilitiesRepository = fakeRepo,
                savRepository = FakeSavRepository(),
                reviewsRepository = FakeReviewsRepository(),
            )

        assertTrue(viewModel.capabilities.value.reviews)
    }

    @Test
    fun `reflete les emissions ulterieures du repository`() {
        val fakeRepo = FakeCapabilitiesRepository(initial = ShopCapabilities(reviews = false))
        val viewModel =
            ClientsTabsViewModel(
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
    fun `expose le compteur SAV non lus du SavRepository`() =
        runTest {
            val fakeSavRepo = FakeSavRepository(initialUnreadCount = 88)

            val viewModel = buildViewModel(savRepository = fakeSavRepo)

            assertEquals(88, viewModel.unreadSavCount.first())
        }

    @Test
    fun `expose le compteur avis en attente du ReviewsRepository`() =
        runTest {
            val fakeReviewsRepo = FakeReviewsRepository(initialPendingCount = 3)

            val viewModel = buildViewModel(reviewsRepository = fakeReviewsRepo)

            assertEquals(3, viewModel.pendingReviewCount.first())
        }
}
