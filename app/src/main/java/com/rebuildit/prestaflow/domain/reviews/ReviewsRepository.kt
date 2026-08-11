package com.rebuildit.prestaflow.domain.reviews

import com.rebuildit.prestaflow.domain.reviews.model.Review
import com.rebuildit.prestaflow.domain.reviews.model.ReviewTrashResult
import com.rebuildit.prestaflow.domain.reviews.model.ReviewsPage

/**
 * Port des Avis — pont vers le module tiers `rbreviews`, capacité `reviews` (cf.
 * [com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities.reviews]). N'appeler ces
 * méthodes que si cette capacité est vraie : sinon le connecteur répond `409 reviews_unavailable`
 * sur toutes les routes. Hors périmètre (volontaire) : modifier le texte d'un avis, import Etsy,
 * réglages du module.
 */
interface ReviewsRepository {
    /** Charge une page de la file de modération (`GET /reviews`, plus récents d'abord). */
    suspend fun fetchPendingReviews(
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): ReviewsPage

    /** Publie un avis (`POST /reviews/{id}/publish`). */
    suspend fun publish(reviewId: Long): Review

    /**
     * Met un avis à la corbeille (`POST /reviews/{id}/trash`) — ⚠️ envoie un e-mail de motif à
     * l'auteur (article L111-7-2, cf. [ReviewRejectionReason]).
     *
     * @throws IllegalArgumentException si [reason] ne respecte pas [ReviewRejectionReason.isValid]
     * — vérifié ICI, avant tout appel réseau, jamais délégué au seul contrôle serveur (défense en
     * profondeur : même principe que le connecteur qui valide "avant toute écriture en base").
     */
    suspend fun trash(
        reviewId: Long,
        reason: String,
    ): ReviewTrashResult

    /** Répond publiquement à un avis (`POST /reviews/{id}/reply`), affiché sous l'avis sur la boutique. */
    suspend fun reply(
        reviewId: Long,
        reply: String,
    ): Review

    companion object {
        const val PAGE_SIZE = 20
    }
}
