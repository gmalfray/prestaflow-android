package com.rebuildit.prestaflow.data.reviews

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
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
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
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
                    .getOrElse { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    }
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
                    }.getOrElse { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    }
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
                }.getOrElse { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    throw error
                }.review.toDomain()
            }
    }
