package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
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
    private val tokenManager: TokenManager,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    private val _authState = MutableStateFlow(initialState())
    override val authState: StateFlow<AuthState> = _authState

    override suspend fun login(shopUrl: String, apiKey: String): AuthResult {
        _authState.value = AuthState.Loading

        val normalizedUrl = when (val result = shopUrlValidator.validate(shopUrl)) {
            is ShopUrlValidator.Result.Valid -> result.normalizedUrl
            is ShopUrlValidator.Result.Invalid -> {
                _authState.value = AuthState.Unauthenticated
                return AuthResult.Failure(AuthFailure.InvalidShopUrl(result))
            }
        }

        val response = runCatching {
            withContext(ioDispatcher) {
                api.login(AuthRequestDto(apiKey = apiKey.trim(), shopUrl = normalizedUrl))
            }
        }

        return response.fold(
            onSuccess = { payload ->
                val token = AuthToken(
                    value = payload.token,
                    expiresAtEpochMillis = payload.expiresIn.takeIf { it > 0 }?.let {
                        System.currentTimeMillis() + it * 1000
                    },
                    scopes = payload.scopes
                )
                tokenManager.update(token)
                _authState.value = AuthState.Authenticated(token)
                AuthResult.Success
            },
            onFailure = { error ->
                Timber.e(error, "Login failed")
                tokenManager.update(null)
                _authState.value = AuthState.Unauthenticated
                val message = networkErrorMapper.map(error)
                val failure = when (error) {
                    is IOException, is HttpException -> AuthFailure.Network(message)
                    else -> AuthFailure.Unknown(message)
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
}
