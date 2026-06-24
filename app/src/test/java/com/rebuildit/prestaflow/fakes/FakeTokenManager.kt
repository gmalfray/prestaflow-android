package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.auth.model.AuthToken

/**
 * Fake en mémoire imitant le comportement de [com.rebuildit.prestaflow.core.security.TokenManager].
 * Expose le token actif pour les assertions de test.
 */
class FakeTokenManager {
    private var currentTokenValue: AuthToken? = null

    fun currentToken(): AuthToken? = currentTokenValue

    fun update(token: AuthToken?) {
        currentTokenValue = token
    }
}
