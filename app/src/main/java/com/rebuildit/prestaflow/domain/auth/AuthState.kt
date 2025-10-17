package com.rebuildit.prestaflow.domain.auth

import com.rebuildit.prestaflow.domain.auth.model.AuthToken

sealed class AuthState {
    data object Unauthenticated : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val token: AuthToken) : AuthState()
}
