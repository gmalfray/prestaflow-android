package com.rebuildit.prestaflow.ui.reviews

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard
import com.rebuildit.prestaflow.core.util.ScreenResumeRefreshGuard.Companion.MIN_INTERVAL_MS
import com.rebuildit.prestaflow.domain.reviews.model.Review
import com.rebuildit.prestaflow.domain.reviews.model.ReviewTrashResult
import com.rebuildit.prestaflow.domain.reviews.model.ReviewsPage
import com.rebuildit.prestaflow.fakes.FakeReviewsRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests du [ReviewsViewModel]. Le point critique : [ReviewsViewModel.onTrash] ne doit JAMAIS
 * appeler le repository si le motif ne respecte pas [com.rebuildit.prestaflow.domain.reviews.ReviewRejectionReason]
 * — c'est la garantie qu'aucun geste de l'UI (bouton, futur swipe) ne peut contourner l'obligation
 * légale (article L111-7-2) en amont du repository lui-même.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeReviewsRepository
    private lateinit var fakeTimeProvider: FakeTimeProvider
    private lateinit var resumeRefreshGuard: ScreenResumeRefreshGuard

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeReviewsRepository()
        fakeTimeProvider = FakeTimeProvider()
        resumeRefreshGuard = ScreenResumeRefreshGuard(fakeTimeProvider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = ReviewsViewModel(fakeRepo, NetworkErrorMapper(), resumeRefreshGuard)

    @Test
    fun `charge la premiere page au demarrage`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)

            val vm = buildViewModel()
            advanceUntilIdle()

            val state = vm.uiState.value as ReviewsUiState.Content
            assertEquals(1, state.reviews.size)
        }

    @Test
    fun `etat Error si le premier chargement echoue`() =
        runTest {
            fakeRepo.shouldThrowOnFetch = true

            val vm = buildViewModel()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is ReviewsUiState.Error)
        }

    @Test
    fun `onLoadMore accumule les avis`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(1L)), hasNext = true, nextOffset = 20)
            val vm = buildViewModel()
            advanceUntilIdle()

            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(2L)), hasNext = false, nextOffset = 40)
            vm.onLoadMore()
            advanceUntilIdle()

            val state = vm.uiState.value as ReviewsUiState.Content
            assertEquals(listOf(1L, 2L), state.reviews.map { it.id })
        }

    // ─── publish ─────────────────────────────────────────────────────────────

    @Test
    fun `onPublish retire l avis de la file apres succes`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            fakeRepo.publishResult = buildReview(812L, validated = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onPublish(812L)
            advanceUntilIdle()

            val state = vm.uiState.value as ReviewsUiState.Content
            assertTrue(state.reviews.isEmpty())
            assertEquals(listOf(812L), fakeRepo.publishCalls)
        }

    // ─── trash — garde-fou motif obligatoire ────────────────────────────────

    @Test
    fun `onTrash n appelle PAS le repository si le motif est trop court`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onTrash(812L, "court")
            advanceUntilIdle()

            assertTrue("trash() ne doit jamais être appelé avec un motif invalide", fakeRepo.trashCalls.isEmpty())
            val state = vm.uiState.value as ReviewsUiState.Content
            assertEquals(1, state.reviews.size)
        }

    @Test
    fun `onTrash n appelle PAS le repository si le motif est vide`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onTrash(812L, "")
            advanceUntilIdle()

            assertTrue(fakeRepo.trashCalls.isEmpty())
        }

    @Test
    fun `onTrash avec un motif valide appelle le repository et retire l avis de la file`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            fakeRepo.trashResult =
                ReviewTrashResult(review = buildReview(812L, deleted = true), authorNotified = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onTrash(812L, "Contenu hors sujet, sans rapport avec le produit vendu.")
            advanceUntilIdle()

            assertEquals(1, fakeRepo.trashCalls.size)
            val state = vm.uiState.value as ReviewsUiState.Content
            assertTrue(state.reviews.isEmpty())
        }

    @Test
    fun `onTrash exactement au seuil de 10 caracteres est accepte`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            fakeRepo.trashResult = ReviewTrashResult(review = buildReview(812L, deleted = true), authorNotified = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onTrash(812L, "1234567890") // exactement 10 caractères
            advanceUntilIdle()

            assertEquals(1, fakeRepo.trashCalls.size)
        }

    // ─── reply ───────────────────────────────────────────────────────────────

    @Test
    fun `onReply met a jour l avis dans la file sans le retirer`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            fakeRepo.replyResult = buildReview(812L, reply = "Merci pour votre retour !")
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onReply(812L, "Merci pour votre retour !")
            advanceUntilIdle()

            val state = vm.uiState.value as ReviewsUiState.Content
            assertEquals(1, state.reviews.size)
            assertEquals("Merci pour votre retour !", state.reviews.first().reply)
        }

    @Test
    fun `onReply ignore un texte vide`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onReply(812L, "   ")
            advanceUntilIdle()

            assertTrue(fakeRepo.replyCalls.isEmpty())
        }

    @Test
    fun `consumeActionFeedback reinitialise message et erreur`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            fakeRepo.publishResult = buildReview(812L, validated = true)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.onPublish(812L)
            advanceUntilIdle()
            vm.consumeActionFeedback()

            assertNull(vm.actionState.value.message)
            assertNull(vm.actionState.value.error)
        }

    // ─── Rattrapage au retour sur l'écran (onScreenResumed) ─────────────────

    @Test
    fun `onScreenResumed declenche un refresh de la liste ET du compteur apres le delai minimal`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = listOf(buildReview(812L)), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeRepo.fetchPendingReviewsCallCount = 0
            fakeRepo.refreshPendingCountCallCount = 0

            fakeTimeProvider.advanceBy(MIN_INTERVAL_MS)
            vm.onScreenResumed()
            advanceUntilIdle()

            assertTrue(
                "Un retour à l'écran après le délai minimal doit recharger la liste",
                fakeRepo.fetchPendingReviewsCallCount > 0,
            )
            assertTrue(
                "Il doit AUSSI rafraîchir le compteur \"en attente\" (pastille du shell), sinon " +
                    "liste et pastille peuvent se contredire",
                fakeRepo.refreshPendingCountCallCount > 0,
            )
        }

    @Test
    fun `onScreenResumed est ignore avant expiration du delai minimal`() =
        runTest {
            fakeRepo.fetchPendingReviewsResult = ReviewsPage(reviews = emptyList(), hasNext = false, nextOffset = 0)
            val vm = buildViewModel()
            advanceUntilIdle()
            fakeRepo.fetchPendingReviewsCallCount = 0

            fakeTimeProvider.advanceBy(MIN_INTERVAL_MS - 1)
            vm.onScreenResumed()
            advanceUntilIdle()

            assertEquals(
                "Un aller-retour rapide entre onglets ne doit pas déclencher un second appel réseau",
                0,
                fakeRepo.fetchPendingReviewsCallCount,
            )
        }

    private fun buildReview(
        id: Long,
        validated: Boolean = false,
        deleted: Boolean = false,
        reply: String? = null,
    ) = Review(
        id = id,
        productId = 305L,
        productName = "Bougie parfumée Lavande",
        authorName = "Julie M.",
        authorEmail = "julie@example.com",
        grade = 4,
        title = "Très satisfaite",
        content = "Odeur agréable, tient longtemps.",
        verifiedBuyer = true,
        validated = validated,
        deleted = deleted,
        reply = reply,
        rejectionReason = null,
        dateAddedIso = "2026-08-10 18:22:00",
    )
}
