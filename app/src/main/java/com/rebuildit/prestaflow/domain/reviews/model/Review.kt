package com.rebuildit.prestaflow.domain.reviews.model

/** Avis client — pont vers le module tiers `rbreviews` (cf. `rebuild-connector` docs/api.md § Avis). */
data class Review(
    val id: Long,
    val productId: Long?,
    val productName: String?,
    val authorName: String,
    val authorEmail: String?,
    val grade: Int,
    val title: String?,
    val content: String,
    val verifiedBuyer: Boolean,
    val validated: Boolean,
    val deleted: Boolean,
    /** Réponse publique du marchand, affichée sous l'avis sur la boutique. */
    val reply: String?,
    /** Motif de rejet — renseigné uniquement si [deleted] est vrai. */
    val rejectionReason: String?,
    val dateAddedIso: String?,
)

/** Page paginée d'avis en attente de modération, issue de `GET /reviews`. */
data class ReviewsPage(
    val reviews: List<Review>,
    val hasNext: Boolean,
    val nextOffset: Int,
)

/**
 * Résultat d'une mise à la corbeille (`POST /reviews/{id}/trash`).
 *
 * [authorNotified] peut être `false` sans que la mise en corbeille ait échoué : un échec d'envoi
 * de l'e-mail de motif (adresse absente, exception) ne fait jamais échouer l'action elle-même
 * côté connecteur — l'UI doit refléter cette nuance plutôt que de toujours annoncer une
 * notification réussie.
 */
data class ReviewTrashResult(
    val review: Review,
    val authorNotified: Boolean,
)
