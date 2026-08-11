package com.rebuildit.prestaflow.data.reviews

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.ApiErrorBodyDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewReplyRequestDto
import com.rebuildit.prestaflow.data.remote.dto.ReviewTrashRequestDto
import com.rebuildit.prestaflow.data.reviews.mapper.toDomain
import com.rebuildit.prestaflow.domain.reviews.ReviewRejectionReason
import com.rebuildit.prestaflow.domain.reviews.ReviewsRepository
import com.rebuildit.prestaflow.domain.reviews.model.Review
import com.rebuildit.prestaflow.domain.reviews.model.ReviewTrashResult
import com.rebuildit.prestaflow.domain.reviews.model.ReviewsPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Instance Json réutilisée pour parser les body d'erreur du connecteur. */
private val errorBodyJson = Json { ignoreUnknownKeys = true }

private const val HTTP_CONFLICT = 409
private const val HTTP_UNPROCESSABLE_ENTITY = 422

@Singleton
class ReviewsRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ReviewsRepository {
        override suspend fun fetchPendingReviews(
            limit: Int,
            offset: Int,
        ): ReviewsPage =
            withContext(ioDispatcher) {
                val response =
                    runCatching { api.getReviews(limit = limit, offset = offset) }.getOrElse { error ->
                        throw translateError(error)
                    }
                val pagination = response.pagination
                ReviewsPage(
                    reviews = response.reviews.map { it.toDomain() },
                    hasNext = pagination?.hasNext == true,
                    nextOffset = pagination?.nextOffset ?: (offset + response.reviews.size),
                )
            }

        override suspend fun publish(reviewId: Long): Review =
            withContext(ioDispatcher) {
                runCatching { api.publishReview(reviewId) }
                    .getOrElse { error -> throw translateError(error) }
                    .review
                    .toDomain()
            }

        override suspend fun trash(
            reviewId: Long,
            reason: String,
        ): ReviewTrashResult {
            // Garde-fou côté app : jamais d'appel réseau avec un motif invalide, même si un
            // appelant contourne la validation UI — même principe que le connecteur, qui valide
            // "avant toute écriture en base" (cf. rebuild-connector docs/api.md § reviews/{id}/trash).
            require(ReviewRejectionReason.isValid(reason)) {
                "Motif de rejet invalide (< ${ReviewRejectionReason.MIN_LENGTH} caractères)"
            }
            return withContext(ioDispatcher) {
                val response =
                    runCatching {
                        api.trashReview(reviewId, ReviewTrashRequestDto(reason = reason.trim()))
                    }.getOrElse { error -> throw translateError(error) }
                ReviewTrashResult(
                    review = response.review.toDomain(),
                    authorNotified = response.authorNotified,
                )
            }
        }

        override suspend fun reply(
            reviewId: Long,
            reply: String,
        ): Review =
            withContext(ioDispatcher) {
                runCatching {
                    api.replyReview(reviewId, ReviewReplyRequestDto(reply = reply))
                }.getOrElse { error -> throw translateError(error) }.review.toDomain()
            }

        /**
         * Traduit les erreurs propres au contrat Avis en message lisible, AVANT de déléguer au
         * [NetworkErrorMapper] générique (qui ne connaît pas `409 reviews_unavailable` ni
         * `422 invalid_rejection_reason` — cf. audit api-contract-guardian). Un `409` est
         * plausible en usage réel : le module `rbreviews` peut être désinstallé entre la lecture
         * des capacités (§ [com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository])
         * et le geste de modération lui-même.
         */
        private fun translateError(error: Throwable): Throwable {
            Timber.w(networkErrorMapper.map(error).toString())
            if (error !is HttpException) return error
            val bodyMessage =
                runCatching {
                    error.response()?.errorBody()?.string()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { errorBodyJson.decodeFromString<ApiErrorBodyDto>(it) }
                }.getOrNull()
            return when (error.code()) {
                HTTP_CONFLICT ->
                    RuntimeException(
                        bodyMessage?.message ?: "Le module Avis n'est plus disponible sur cette boutique",
                        error,
                    )
                HTTP_UNPROCESSABLE_ENTITY ->
                    RuntimeException(
                        bodyMessage?.message ?: "Motif de rejet refusé par le serveur (10 caractères minimum)",
                        error,
                    )
                else -> error
            }
        }
    }
