package com.rebuildit.prestaflow.ui.sav

import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.sav.model.SavThread
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus

/**
 * Filtre de la liste SAV. [ALL] laisse le connecteur trier lui-même — sans filtre, `GET /sav`
 * renvoie tous statuts, non-clos d'abord (comportement par défaut du connecteur), ce qui donne
 * naturellement « les fils ouverts en premier » sans qu'on ait à le recalculer côté app.
 */
enum class SavStatusFilter(
    val threadStatus: SavThreadStatus?,
) {
    ALL(null),
    OPEN(SavThreadStatus.OPEN),
    AWAITING_CUSTOMER_REPLY(SavThreadStatus.AWAITING_CUSTOMER_REPLY),
    AWAITING_MERCHANT_REPLY(SavThreadStatus.AWAITING_MERCHANT_REPLY),
    CLOSED(SavThreadStatus.CLOSED),
}

/**
 * État d'écran de la liste SAV.
 *
 * [Content] reste l'état affiché même quand [Content.threads] est vide (recherche/filtre sans
 * résultat) : l'écran garde les chips de filtre visibles, comme la liste Clients (cf.
 * [com.rebuildit.prestaflow.ui.clients.ClientsScreen]). [Error] n'est utilisé qu'à l'échec du
 * tout premier chargement (aucune donnée en cache à montrer) ; une fois du contenu affiché, une
 * erreur ultérieure reste dans [Content.error] (bannière + retry, contenu conservé).
 */
sealed interface SavUiState {
    data object Loading : SavUiState

    data class Content(
        val threads: List<SavThread> = emptyList(),
        val filter: SavStatusFilter = SavStatusFilter.ALL,
        val hasNextPage: Boolean = false,
        val isLoadingMore: Boolean = false,
        val isRefreshing: Boolean = false,
        val error: UiText? = null,
    ) : SavUiState

    data class Error(val message: UiText) : SavUiState
}
