package com.rebuildit.prestaflow.core.security

import com.rebuildit.prestaflow.domain.auth.model.AuthToken

interface TokenStorage {
    fun persist(token: AuthToken)
    fun read(): AuthToken?
    fun clear()
}
