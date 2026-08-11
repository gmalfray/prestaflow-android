package com.rebuildit.prestaflow.ui.sav

import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.sav.model.SavThreadDetail

sealed interface SavThreadDetailUiState {
    data object Loading : SavThreadDetailUiState

    data class Success(val detail: SavThreadDetail) : SavThreadDetailUiState

    data object Error : SavThreadDetailUiState
}

/**
 * État des actions du fil (changement de statut, réponse). Séparé de [SavThreadDetailUiState]
 * pour ne pas remplacer tout l'écran par un état de chargement pendant une action ponctuelle —
 * même principe que `OrderActionState` (cf. [com.rebuildit.prestaflow.ui.orders.OrderActionState]).
 */
data class SavThreadActionState(
    val inProgress: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
)
