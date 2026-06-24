package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.core.security.TokenStorage
import com.rebuildit.prestaflow.domain.auth.model.AuthToken

/**
 * Implémentation en mémoire de [TokenStorage] pour les tests JVM purs.
 */
class FakeTokenStorage(initial: AuthToken? = null) : TokenStorage {
    private var stored: AuthToken? = initial

    override fun persist(token: AuthToken) {
        stored = token
    }

    override fun read(): AuthToken? = stored

    override fun clear() {
        stored = null
    }
}
