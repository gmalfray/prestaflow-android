package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.core.security.TokenManager
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.domain.auth.AuthFailure
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("LongParameterList") // Repository Hilt : dépendances réseau/sécurité/dispatcher
class AuthRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val shopUrlValidator: ShopUrlValidator,
        private val endpointManager: ApiEndpointManager,
        private val tokenManager: TokenManager,
        private val connectionStore: ShopConnectionStore,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState

        private val _connections = MutableStateFlow<List<ShopConnection>>(emptyList())
        override val connections: StateFlow<List<ShopConnection>> = _connections

        init {
            migrateLegacySingleShopIfNeeded()
            refreshConnections()
            val token = tokenManager.currentToken()?.takeUnless { it.isExpired }
            _authState.value = if (token != null) AuthState.Authenticated(token) else AuthState.Unauthenticated
        }

        override suspend fun login(
            shopUrl: String,
            apiKey: String,
        ): AuthResult = addConnection(shopUrl, apiKey, label = "")

        override suspend fun addConnection(
            shopUrl: String,
            apiKey: String,
            label: String,
        ): AuthResult {
            _authState.value = AuthState.Loading
            val previousActive = activeConnection()

            return when (val outcome = authenticate(shopUrl, apiKey)) {
                is AuthOutcome.Success -> {
                    val finalLabel = label.trim().ifEmpty { labelFor(outcome.normalizedUrl) }
                    val connection =
                        ShopConnection(
                            id = outcome.normalizedUrl,
                            shopUrl = outcome.normalizedUrl,
                            label = finalLabel,
                            token = outcome.token,
                        )
                    val updated = connectionStore.read().filterNot { it.id == connection.id } + connection
                    connectionStore.write(updated)
                    activate(connection)
                    AuthResult.Success
                }
                is AuthOutcome.Failure -> {
                    // Restaure la boutique précédemment active (le cas échéant).
                    activate(previousActive)
                    AuthResult.Failure(outcome.failure)
                }
            }
        }

        override suspend fun switchActiveConnection(id: String) {
            withContext(ioDispatcher) {
                val connection = connectionStore.read().firstOrNull { it.id == id } ?: return@withContext
                activate(connection)
            }
        }

        override suspend fun removeConnection(id: String) {
            withContext(ioDispatcher) {
                val wasActive = connectionStore.getActiveId() == id
                val remaining = connectionStore.read().filterNot { it.id == id }
                connectionStore.write(remaining)
                if (wasActive) {
                    activate(remaining.firstOrNull())
                } else {
                    refreshConnections()
                }
            }
        }

        override suspend fun logout() {
            withContext(ioDispatcher) {
                connectionStore.clear()
                tokenManager.update(null)
                endpointManager.clearOverride()
            }
            _connections.value = emptyList()
            _authState.value = AuthState.Unauthenticated
        }

        override suspend fun getActiveToken(): AuthToken? =
            withContext(ioDispatcher) {
                tokenManager.currentToken()?.takeUnless { it.isExpired }
            }

        // ─── Internes ────────────────────────────────────────────────────────────

        private sealed interface AuthOutcome {
            data class Success(val normalizedUrl: String, val token: AuthToken) : AuthOutcome

            data class Failure(val failure: AuthFailure) : AuthOutcome
        }

        /** Valide l'URL, route l'appel vers la boutique, se connecte et construit le token. */
        @Suppress("ReturnCount")
        private suspend fun authenticate(
            shopUrl: String,
            apiKey: String,
        ): AuthOutcome {
            val normalizedUrl =
                when (val result = shopUrlValidator.validate(shopUrl)) {
                    is ShopUrlValidator.Result.Valid -> result.normalizedUrl
                    is ShopUrlValidator.Result.Invalid ->
                        return AuthOutcome.Failure(AuthFailure.InvalidShopUrl(result))
                }

            val apiBaseUrl =
                endpointManager.buildApiBaseUrl(normalizedUrl)
                    ?: return AuthOutcome.Failure(
                        AuthFailure.InvalidShopUrl(ShopUrlValidator.Result.Invalid.Malformed),
                    )

            // Route l'appel de login vers cette boutique (sans persister tant que ça n'a pas réussi).
            endpointManager.setActiveBaseUrl(apiBaseUrl, normalizedUrl, persist = false)

            val response =
                runCatching {
                    withContext(ioDispatcher) {
                        api.login(AuthRequestDto(apiKey = apiKey.trim(), shopUrl = normalizedUrl))
                    }
                }

            return response.fold(
                onSuccess = { payload ->
                    val token =
                        AuthToken(
                            value = payload.token,
                            expiresAtEpochMillis =
                                payload.expiresIn.takeIf { it > 0 }?.let {
                                    System.currentTimeMillis() + it * MILLIS_PER_SECOND
                                },
                            scopes = payload.scopes,
                        )
                    Timber.i("Login OK pour shopUrl=%s (scopes=%s)", normalizedUrl, payload.scopes.joinToString())
                    AuthOutcome.Success(normalizedUrl, token)
                },
                onFailure = { error ->
                    AuthOutcome.Failure(mapLoginFailure(error, normalizedUrl))
                },
            )
        }

        private fun mapLoginFailure(
            error: Throwable,
            normalizedUrl: String,
        ): AuthFailure {
            val message = networkErrorMapper.map(error)
            return when (error) {
                is IOException -> {
                    Timber.e(error, "Login échec réseau (shopUrl=%s)", normalizedUrl)
                    AuthFailure.Network(message)
                }
                is HttpException -> {
                    val body =
                        runCatching { error.response()?.errorBody()?.string() }
                            .getOrNull()?.take(MAX_ERROR_BODY_LENGTH)
                    Timber.e(
                        error,
                        "Login échec HTTP %d (shopUrl=%s, body=%s)",
                        error.code(),
                        normalizedUrl,
                        body ?: "<empty>",
                    )
                    AuthFailure.Network(message)
                }
                else -> {
                    Timber.e(error, "Login échec inattendu (shopUrl=%s)", normalizedUrl)
                    AuthFailure.Unknown(message)
                }
            }
        }

        /** Rend une connexion active (endpoint + token + persistance) ; null = déconnecté. */
        private fun activate(connection: ShopConnection?) {
            if (connection == null) {
                tokenManager.update(null)
                endpointManager.clearOverride()
                connectionStore.setActiveId(null)
                _authState.value = AuthState.Unauthenticated
                refreshConnections()
                return
            }
            endpointManager.buildApiBaseUrl(connection.shopUrl)?.let { baseUrl ->
                endpointManager.setActiveBaseUrl(baseUrl, connection.shopUrl, persist = true)
            }
            tokenManager.update(connection.token)
            connectionStore.setActiveId(connection.id)
            _authState.value = AuthState.Authenticated(connection.token)
            refreshConnections()
        }

        private fun activeConnection(): ShopConnection? {
            val activeId = connectionStore.getActiveId() ?: return null
            return connectionStore.read().firstOrNull { it.id == activeId }
        }

        private fun refreshConnections() {
            val activeId = connectionStore.getActiveId()
            _connections.value = connectionStore.read().map { it.copy(isActive = it.id == activeId) }
        }

        /** Migre un utilisateur déjà connecté (mono-boutique) vers une connexion. */
        private fun migrateLegacySingleShopIfNeeded() {
            if (connectionStore.read().isNotEmpty()) return
            val token = tokenManager.currentToken() ?: return
            val shopUrl = endpointManager.getStoredShopUrl() ?: return
            val migrated =
                ShopConnection(id = shopUrl, shopUrl = shopUrl, label = labelFor(shopUrl), token = token)
            connectionStore.write(listOf(migrated))
            connectionStore.setActiveId(migrated.id)
            Timber.i("Migration mono->multi boutique : %s", shopUrl)
        }

        private fun labelFor(shopUrl: String): String = shopUrl.substringAfter("://").trimEnd('/').ifEmpty { shopUrl }

        private companion object {
            const val MAX_ERROR_BODY_LENGTH = 1024
            const val MILLIS_PER_SECOND = 1000L
        }
    }
