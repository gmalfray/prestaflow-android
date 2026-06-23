package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Fake en mémoire de [AuthRepository].
 * Permet d'émettre des changements de boutique active via [emitConnections].
 */
class FakeAuthRepository : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Authenticated(fakeToken()))
    override val authState: StateFlow<AuthState> = _authState

    private val _connections = MutableStateFlow<List<ShopConnection>>(listOf(singleActiveConnection()))
    override val connections: StateFlow<List<ShopConnection>> = _connections

    fun emitConnections(connections: List<ShopConnection>) {
        _connections.value = connections
    }

    override suspend fun login(shopUrl: String, apiKey: String): AuthResult = AuthResult.Success

    override suspend fun addConnection(shopUrl: String, apiKey: String, label: String): AuthResult =
        AuthResult.Success

    override suspend fun switchActiveConnection(id: String) = Unit

    override suspend fun removeConnection(id: String) = Unit

    override suspend fun logout() {
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun getActiveToken(): AuthToken = fakeToken()

    override suspend fun refreshActiveToken(): Boolean = true

    companion object {
        fun fakeToken(expired: Boolean = false): AuthToken =
            AuthToken(
                value = "fake-token",
                expiresAtEpochMillis = if (expired) 1L else Long.MAX_VALUE,
                scopes = listOf("orders", "products", "customers"),
            )

        fun singleActiveConnection(id: String = "https://shop.test"): ShopConnection =
            ShopConnection(
                id = id,
                shopUrl = id,
                label = "Test Shop",
                token = fakeToken(),
                apiKey = "key-123",
                isActive = true,
            )
    }
}
