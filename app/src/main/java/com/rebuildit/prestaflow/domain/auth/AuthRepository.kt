package com.rebuildit.prestaflow.domain.auth

import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun login(shopUrl: String, apiKey: String): AuthResult

    suspend fun logout()

    suspend fun getActiveToken(): AuthToken?
}
