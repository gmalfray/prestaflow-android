package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Avis clients — pont vers le module tiers `rbreviews` (cf. `rebuild-connector` docs/api.md §
 * Avis). Toutes les routes répondent `409 reviews_unavailable` si `rbreviews` n'est pas
 * installé/actif — vérifier la capacité `reviews` avant d'appeler.
 */
@Serializable
data class ReviewListResponseDto(
    @SerialName("reviews") val reviews: List<ReviewDto> = emptyList(),
    @SerialName("pagination") val pagination: PaginationDto? = null,
)

@Serializable
data class ReviewDto(
    @SerialName("id") val id: Long,
    @SerialName("product") val product: ReviewProductDto? = null,
    @SerialName("author") val author: ReviewAuthorDto? = null,
    @SerialName("grade") val grade: Int = 0,
    @SerialName("title") val title: String? = null,
    @SerialName("content") val content: String = "",
    @SerialName("verified_buyer") val verifiedBuyer: Boolean = false,
    @SerialName("validated") val validated: Boolean = false,
    @SerialName("deleted") val deleted: Boolean = false,
    @SerialName("reply") val reply: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("date_add") val dateAdd: String? = null,
)

@Serializable
data class ReviewProductDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class ReviewAuthorDto(
    @SerialName("name") val name: String? = null,
    @SerialName("email") val email: String? = null,
)

@Serializable
data class ReviewPublishResponseDto(
    @SerialName("review") val review: ReviewDto,
)

@Serializable
data class ReviewTrashRequestDto(
    @SerialName("reason") val reason: String,
)

/**
 * Réponse de `POST /reviews/{id}/trash` (200). ⚠️ Envoie un e-mail de motif à l'auteur
 * (article L111-7-2) — cf. [com.rebuildit.prestaflow.domain.reviews.ReviewRejectionReason].
 */
@Serializable
data class ReviewTrashResponseDto(
    @SerialName("review") val review: ReviewDto,
    @SerialName("author_notified") val authorNotified: Boolean = false,
)

@Serializable
data class ReviewReplyRequestDto(
    @SerialName("reply") val reply: String,
)

@Serializable
data class ReviewReplyResponseDto(
    @SerialName("review") val review: ReviewDto,
)
