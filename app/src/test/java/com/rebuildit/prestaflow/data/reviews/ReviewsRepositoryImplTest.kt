package com.rebuildit.prestaflow.data.reviews

import app.cash.turbine.test
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.dto.PaginationDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewAuthorDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewListResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewProductDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewPublishResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewReplyResponseDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewTrashResponseDto
import com.rebuildit.prestaflow.fakes.FakePrestaFlowApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.Response as OkHttpResponse

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsRepositoryImplTest {
    private lateinit var fakeApi: FakePrestaFlowApi
    private lateinit var repository: ReviewsRepositoryImpl

    @Before
    fun setUp() {
        fakeApi = FakePrestaFlowApi()
        repository =
            ReviewsRepositoryImpl(
                api = fakeApi,
                networkErrorMapper = NetworkErrorMapper(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
    }

    // ─── pendingReviewCount / refreshPendingCount ──────────────────────────────

    @Test
    fun `pendingReviewCount emet 0 avant tout refresh`() =
        runTest {
            repository.pendingReviewCount.test {
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshPendingCount compte tous les avis de la premiere page (deja filtree cote connecteur)`() =
        runTest {
            fakeApi.reviewsResponse =
                ReviewListResponseDto(
                    reviews = listOf(buildReviewDto(id = 1L), buildReviewDto(id = 2L), buildReviewDto(id = 3L)),
                )

            repository.refreshPendingCount()

            repository.pendingReviewCount.test {
                assertEquals(3, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `refreshPendingCount conserve la derniere valeur connue en cas d echec reseau`() =
        runTest {
            fakeApi.reviewsResponse = ReviewListResponseDto(reviews = listOf(buildReviewDto(id = 1L)))
            repository.refreshPendingCount()

            fakeApi.reviewsException = RuntimeException("Erreur réseau simulée")
            repository.refreshPendingCount()

            repository.pendingReviewCount.test {
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `fetchPendingReviews mappe la liste et la pagination`() =
        runTest {
            fakeApi.reviewsResponse =
                ReviewListResponseDto(
                    reviews = listOf(buildReviewDto(id = 812L)),
                    pagination = PaginationDto(hasNext = true, nextOffset = 20),
                )

            val page = repository.fetchPendingReviews()

            assertEquals(1, page.reviews.size)
            assertEquals("Julie M.", page.reviews.first().authorName)
            assertTrue(page.hasNext)
            assertEquals(20, page.nextOffset)
        }

    @Test
    fun `publish mappe l avis publie`() =
        runTest {
            fakeApi.publishReviewResponse = ReviewPublishResponseDto(review = buildReviewDto(id = 812L, validated = true))

            val review = repository.publish(812L)

            assertTrue(review.validated)
        }

    // ─── trash — garde-fou motif obligatoire (article L111-7-2) ────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `trash refuse un motif trop court AVANT tout appel reseau`() =
        runTest {
            repository.trash(812L, "court")

            assertTrue("Aucun appel réseau ne doit avoir eu lieu", fakeApi.trashReviewCalls.isEmpty())
        }

    @Test(expected = IllegalArgumentException::class)
    fun `trash refuse un motif vide`() =
        runTest {
            repository.trash(812L, "")
        }

    @Test
    fun `trash accepte un motif de 10 caracteres ou plus et transmet le motif trimme`() =
        runTest {
            fakeApi.trashReviewResponse =
                ReviewTrashResponseDto(
                    review = buildReviewDto(id = 812L, deleted = true, rejectionReason = "Contenu hors sujet, sans rapport."),
                    authorNotified = true,
                )

            val result = repository.trash(812L, "  Contenu hors sujet, sans rapport.  ")

            assertEquals(812L to "Contenu hors sujet, sans rapport.", fakeApi.trashReviewCalls.last())
            assertTrue(result.authorNotified)
            assertTrue(result.review.deleted)
        }

    @Test
    fun `trash n echoue pas si author_notified est false`() =
        runTest {
            fakeApi.trashReviewResponse =
                ReviewTrashResponseDto(review = buildReviewDto(id = 812L, deleted = true), authorNotified = false)

            val result = repository.trash(812L, "Motif suffisamment long pour être valide")

            assertFalse(result.authorNotified)
        }

    @Test
    fun `reply mappe la reponse publique`() =
        runTest {
            fakeApi.replyReviewResponse = ReviewReplyResponseDto(review = buildReviewDto(id = 812L, reply = "Merci !"))

            val review = repository.reply(812L, "Merci !")

            assertEquals("Merci !", review.reply)
        }

    // ─── Traduction des erreurs 409/422 (audit api-contract-guardian) ─────────

    @Test
    fun `publish sur 409 expose le message reviews_unavailable du connecteur`() =
        runTest {
            fakeApi.publishReviewException = buildHttpException(409, """{"error":"reviews_unavailable","message":"Module désinstallé"}""")

            val error = runCatching { repository.publish(812L) }.exceptionOrNull()

            assertTrue(error?.message?.contains("Module désinstallé") == true)
        }

    @Test
    fun `publish sur 409 sans body retombe sur un message par defaut`() =
        runTest {
            fakeApi.publishReviewException = buildHttpException(409, "")

            val error = runCatching { repository.publish(812L) }.exceptionOrNull()

            assertTrue(error?.message?.contains("disponible") == true)
        }

    @Test
    fun `trash sur 422 expose un message explicite sur le motif`() =
        runTest {
            fakeApi.trashReviewException = buildHttpException(422, """{"error":"invalid_rejection_reason"}""")

            val error = runCatching { repository.trash(812L, "Motif suffisamment long pour passer la validation locale") }.exceptionOrNull()

            assertTrue(error?.message?.contains("Motif") == true || error?.message?.contains("motif") == true)
        }

    @Test
    fun `reply sur une erreur HTTP non geree conserve l exception d origine`() =
        runTest {
            fakeApi.replyReviewException = buildHttpException(500, "")

            val error = runCatching { repository.reply(812L, "Merci !") }.exceptionOrNull()

            assertTrue(error is HttpException)
        }

    private fun buildHttpException(
        code: Int,
        body: String,
    ): HttpException {
        val rawResponse =
            OkHttpResponse.Builder()
                .request(Request.Builder().url("https://shop.test/module/rebuildconnector/api/reviews/812/publish").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Error")
                .build()
        val retrofitResponse = Response.error<Any>(body.toResponseBody("application/json".toMediaTypeOrNull()), rawResponse)
        return HttpException(retrofitResponse)
    }

    private fun buildReviewDto(
        id: Long,
        validated: Boolean = false,
        deleted: Boolean = false,
        reply: String? = null,
        rejectionReason: String? = null,
    ) = ReviewDto(
        id = id,
        product = ReviewProductDto(id = 305L, name = "Bougie parfumée Lavande"),
        author = ReviewAuthorDto(name = "Julie M.", email = "julie@example.com"),
        grade = 4,
        title = "Très satisfaite",
        content = "Odeur agréable, tient longtemps.",
        verifiedBuyer = true,
        validated = validated,
        deleted = deleted,
        reply = reply,
        rejectionReason = rejectionReason,
        dateAdd = "2026-08-10 18:22:00",
    )
}
