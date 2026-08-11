package com.rebuildit.prestaflow.ui.reviews

import com.rebuildit.prestaflow.core.ui.UiText

/**
 * État d'écran des Avis — lot socle : uniquement chargement/vide/erreur, pas de modèle d'avis
 * (cf. étude `rebuild-it/docs/app-avis-sav.md`). Cet écran n'est monté que si la capacité
 * `reviews` est présente (module `rbreviews` installé), cf.
 * [com.rebuildit.prestaflow.ui.clients.ClientsSection.visibleSections].
 */
sealed interface ReviewsUiState {
    data object Loading : ReviewsUiState

    data object Empty : ReviewsUiState

    data class Error(val message: UiText) : ReviewsUiState
}
