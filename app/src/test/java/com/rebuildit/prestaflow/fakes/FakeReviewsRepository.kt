package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.reviews.ReviewRejectionReason
import com.rebuildit.prestaflow.domain.reviews.ReviewsRepository
import com.rebuildit.prestaflow.domain.reviews.model.Review
import com.rebuildit.prestaflow.domain.reviews.model.ReviewTrashResult
import com.rebuildit.prestaflow.domain.reviews.model.ReviewsPage

/**
 * Fake en mémoire de [ReviewsRepository]. Reproduit le garde-fou de l'implémentation réelle sur
 * [trash] (motif invalide → exception, AVANT tout accès réseau) : les tests de ViewModel doivent
 * pouvoir vérifier ce comportement sans dépendre de [com.rebuildit.prestaflow.data.reviews.ReviewsRepositoryImpl].
 */
class FakeReviewsRepository : ReviewsRepository {
    var fetchPendingReviewsResult: ReviewsPage = ReviewsPage(reviews = emptyList(), hasNext = false, nextOffset = 0)
    var shouldThrowOnFetch = false

    override suspend fun fetchPendingReviews(
        limit: Int,
        offset: Int,
    ): ReviewsPage {
        if (shouldThrowOnFetch) throw RuntimeException("Erreur réseau fetchPendingReviews simulée")
        return fetchPendingReviewsResult
    }

    var publishResult: Review? = null
    var shouldThrowOnPublish = false
    val publishCalls = mutableListOf<Long>()

    override suspend fun publish(reviewId: Long): Review {
        publishCalls.add(reviewId)
        if (shouldThrowOnPublish) throw RuntimeException("Erreur réseau publish simulée")
        return checkNotNull(publishResult) { "publishResult non configuré dans le fake" }
    }

    var trashResult: ReviewTrashResult? = null
    var shouldThrowOnTrash = false
    val trashCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun trash(
        reviewId: Long,
        reason: String,
    ): ReviewTrashResult {
        require(ReviewRejectionReason.isValid(reason)) {
            "Motif de rejet invalide (< ${ReviewRejectionReason.MIN_LENGTH} caractères)"
        }
        trashCalls.add(reviewId to reason)
        if (shouldThrowOnTrash) throw RuntimeException("Erreur réseau trash simulée")
        return checkNotNull(trashResult) { "trashResult non configuré dans le fake" }
    }

    var replyResult: Review? = null
    var shouldThrowOnReply = false
    val replyCalls = mutableListOf<Pair<Long, String>>()

    override suspend fun reply(
        reviewId: Long,
        reply: String,
    ): Review {
        replyCalls.add(reviewId to reply)
        if (shouldThrowOnReply) throw RuntimeException("Erreur réseau reply simulée")
        return checkNotNull(replyResult) { "replyResult non configuré dans le fake" }
    }
}
