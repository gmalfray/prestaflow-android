package com.rebuildit.prestaflow.domain.auth

import com.rebuildit.prestaflow.domain.auth.model.AuthToken

sealed class AuthState {
    data object Unauthenticated : AuthState()

    data object Loading : AuthState()

    data class Authenticated(val token: AuthToken) : AuthState()
}

/**
 * Scopes du jeton actif, vide si non authentifié — cf.
 * [com.rebuildit.prestaflow.domain.auth.model.AuthScopes]. Lecture pratique pour tout code qui a
 * besoin de vérifier un droit sans dérouler le `when` sur [AuthState] à chaque fois.
 */
val AuthState.scopes: Set<String>
    get() = (this as? AuthState.Authenticated)?.token?.scopes?.toSet() ?: emptySet()
