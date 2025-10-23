package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.security.TokenManager
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.domain.auth.AuthFailure
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: PrestaFlowApi,
    private val shopUrlValidator: ShopUrlValidator,
    private val endpointManager: ApiEndpointManager,
    private val tokenManager: TokenManager,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    private val _authState = MutableStateFlow(initialState())
    override val authState: StateFlow<AuthState> = _authState

    override suspend fun login(shopUrl: String, apiKey: String): AuthResult {
        _authState.value = AuthState.Loading

        val normalizedUrl = when (val result = shopUrlValidator.validate(shopUrl)) {
            is ShopUrlValidator.Result.Valid -> {
                Timber.d("Shop URL normalized from %s to %s", shopUrl, result.normalizedUrl)
                result.normalizedUrl
            }
            is ShopUrlValidator.Result.Invalid -> {
                Timber.w("Shop URL validation failed for input=%s (reason=%s)", shopUrl, result)
                _authState.value = AuthState.Unauthenticated
                return AuthResult.Failure(AuthFailure.InvalidShopUrl(result))
            }
        }

        val apiBaseUrl = endpointManager.buildApiBaseUrl(normalizedUrl)
        Timber.d("Computed API base URL for shopUrl=%s -> %s", normalizedUrl, apiBaseUrl)
        if (apiBaseUrl == null) {
            Timber.w("Unable to build API base URL for shopUrl=%s", normalizedUrl)
            _authState.value = AuthState.Unauthenticated
            return AuthResult.Failure(AuthFailure.InvalidShopUrl(ShopUrlValidator.Result.Invalid.Malformed))
        }

        endpointManager.setActiveBaseUrl(apiBaseUrl, normalizedUrl, persist = false)
        Timber.d("Attempting login against %s (shopUrl=%s)", apiBaseUrl, normalizedUrl)

        val response = runCatching {
            withContext(ioDispatcher) {
                api.login(AuthRequestDto(apiKey = apiKey.trim(), shopUrl = normalizedUrl))
            }
        }

        return response.fold(
            onSuccess = { payload ->
                endpointManager.setActiveBaseUrl(apiBaseUrl, normalizedUrl, persist = true)
                val token = AuthToken(
                    value = payload.token,
                    expiresAtEpochMillis = payload.expiresIn.takeIf { it > 0 }?.let {
                        System.currentTimeMillis() + it * 1000
                    },
                    scopes = payload.scopes
                )
                tokenManager.update(token)
                _authState.value = AuthState.Authenticated(token)
                Timber.i(
                    "Login succeeded for shopUrl=%s (scopes=%s, expiresAt=%s)",
                    normalizedUrl,
                    payload.scopes.joinToString(),
                    token.expiresAtEpochMillis
                )
                AuthResult.Success
            },
            onFailure = { error ->
                tokenManager.update(null)
                _authState.value = AuthState.Unauthenticated
                val message = networkErrorMapper.map(error)
                val failure = when (error) {
                    is IOException -> {
                        Timber.e(
                            error,
                            "Login failed due to network error (baseUrl=%s, shopUrl=%s)",
                            apiBaseUrl,
                            normalizedUrl
                        )
                        AuthFailure.Network(message)
                    }
                    is HttpException -> {
                        val statusCode = error.code()
                        val requestUrl = error.response()?.raw()?.request?.url?.toString()
                        val errorPayload = runCatching { error.response()?.errorBody()?.string() }
                            .getOrNull()
                            ?.take(MAX_ERROR_BODY_LENGTH)
                        Timber.e(
                            error,
                            "Login failed with HTTP %d (baseUrl=%s, shopUrl=%s, requestUrl=%s, errorBody=%s)",
                            statusCode,
                            apiBaseUrl,
                            normalizedUrl,
                            requestUrl ?: "n/a",
                            errorPayload ?: "<empty>"
                        )
                        AuthFailure.Network(message)
                    }
                    else -> {
                        Timber.e(
                            error,
                            "Login failed with unexpected exception (baseUrl=%s, shopUrl=%s, type=%s)",
                            apiBaseUrl,
                            normalizedUrl,
                            error::class.java.name
                        )
                        AuthFailure.Unknown(message)
                    }
                }
                AuthResult.Failure(failure)
            }
        )
    }

    override suspend fun logout() {
        withContext(ioDispatcher) {
            tokenManager.update(null)
        }
        _authState.value = AuthState.Unauthenticated
    }

    override suspend fun getActiveToken(): AuthToken? = withContext(ioDispatcher) {
        tokenManager.currentToken()?.takeUnless { it.isExpired }
    }

    private fun initialState(): AuthState {
        val token = tokenManager.currentToken()?.takeUnless { it.isExpired }
        return if (token != null) AuthState.Authenticated(token) else AuthState.Unauthenticated
    }

    companion object {
        private const val MAX_ERROR_BODY_LENGTH = 1024
    }
}
