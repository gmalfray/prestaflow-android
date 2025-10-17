package com.rebuildit.prestaflow.core.security

import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val storage: TokenStorage,
    private val inMemoryTokenProvider: InMemoryTokenProvider
) {

    private val _tokenFlow = MutableStateFlow(storage.read())
    val tokenFlow: StateFlow<AuthToken?> = _tokenFlow

    init {
        inMemoryTokenProvider.updateToken(_tokenFlow.value?.value)
    }

    fun currentToken(): AuthToken? = _tokenFlow.value

    fun update(token: AuthToken?) {
        if (token == null) {
            storage.clear()
            _tokenFlow.value = null
            inMemoryTokenProvider.clear()
        } else {
            storage.persist(token)
            _tokenFlow.value = token
            inMemoryTokenProvider.updateToken(token.value)
        }
    }
}
