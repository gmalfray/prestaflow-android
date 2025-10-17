package com.rebuildit.prestaflow.core.security

import com.rebuildit.prestaflow.data.remote.interceptor.AuthInterceptor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class InMemoryTokenProvider @Inject constructor() : AuthInterceptor.TokenProvider {

    private val accessTokenFlow = MutableStateFlow<String?>(null)

    override fun getAccessToken(): String? = accessTokenFlow.value

    fun observeAccessToken(): StateFlow<String?> = accessTokenFlow

    fun updateToken(newToken: String?) {
        accessTokenFlow.value = newToken
    }

    fun clear() {
        accessTokenFlow.value = null
    }
}
