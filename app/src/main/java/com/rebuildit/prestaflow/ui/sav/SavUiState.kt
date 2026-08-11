package com.rebuildit.prestaflow.ui.sav

import com.rebuildit.prestaflow.core.ui.UiText

/**
 * État d'écran du SAV — lot socle : uniquement chargement/vide/erreur, pas de modèle de fil
 * (cf. [com.rebuildit.prestaflow.domain.sav.SavRepository]). Le lot SAV proprement dit, une fois
 * le contrat connecteur figé, ajoutera un état `Content(threads: List<...>)`.
 */
sealed interface SavUiState {
    data object Loading : SavUiState

    data object Empty : SavUiState

    data class Error(val message: UiText) : SavUiState
}
