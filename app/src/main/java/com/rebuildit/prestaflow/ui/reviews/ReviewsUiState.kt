package com.rebuildit.prestaflow.ui.reviews

import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.reviews.model.Review

/**
 * État d'écran de la file de modération des avis. Cet écran n'est monté que si la capacité
 * `reviews` est présente (module `rbreviews` installé), cf.
 * [com.rebuildit.prestaflow.ui.clients.ClientsSection.visibleSections].
 *
 * [Content] reste affiché même quand [Content.reviews] est vide (file de modération vide, cas le
 * plus fréquent en prod — volume faible mesuré dans l'étude `rebuild-it/docs/app-avis-sav.md`).
 */
sealed interface ReviewsUiState {
    data object Loading : ReviewsUiState

    data class Content(
        val reviews: List<Review> = emptyList(),
        val hasNextPage: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: UiText? = null,
    ) : ReviewsUiState

    data class Error(val message: UiText) : ReviewsUiState
}

/** État des actions ponctuelles (publier / corbeille / répondre), séparé de [ReviewsUiState]. */
data class ReviewActionState(
    val inProgress: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
)
