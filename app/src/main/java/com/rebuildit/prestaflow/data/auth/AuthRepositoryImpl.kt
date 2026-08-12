package com.rebuildit.prestaflow.data.auth

import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.core.security.TokenManager
import com.rebuildit.prestaflow.data.local.db.LocalCacheStore
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.domain.auth.AuthFailure
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthResult
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.ShopUrlValidator
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private val loginApiClient: LoginApiClientContract,
        private val shopUrlValidator: ShopUrlValidator,
        private val endpointManager: ApiEndpointManager,
        private val tokenManager: TokenManager,
        private val connectionStore: ShopConnectionStore,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
        private val localCacheStore: LocalCacheStore,
    ) : AuthRepository {
        private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
        override val authState: StateFlow<AuthState> = _authState

        private val _connections = MutableStateFlow<List<ShopConnection>>(emptyList())
        override val connections: StateFlow<List<ShopConnection>> = _connections

        // Sérialise les rafraîchissements de jeton (plusieurs 401 concurrents → un seul re-login).
        private val refreshMutex = Mutex()

        // Fire-and-forget pour refreshScopesIfMissing() : ce correctif doit s'exécuter en tâche de
        // fond dès init(), qui n'est pas une coroutine — même pattern que FcmRegistrationManager.
        private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

        init {
            migrateLegacySingleShopIfNeeded()
            val active = activeConnection()
            when {
                active == null -> {
                    refreshConnections()
                    _authState.value = AuthState.Unauthenticated
                }
                !active.token.isExpired || active.apiKey.isNotBlank() -> {
                    // Jeton valide, OU expiré mais re-loginnable via la clé API conservée :
                    // on réactive la boutique (endpoint + token) sans bloquer sur l'écran de
                    // connexion. L'Authenticator OkHttp rafraîchira le jeton au 1er 401.
                    activate(active)
                    // Connexion persistée AVANT le stockage des scopes (cf. Javadoc de
                    // refreshScopesIfMissing) : le jeton publié ci-dessus porte des scopes vides,
                    // ce qui masquerait à tort SAV/Avis (capacité != droit) — potentiellement pour
                    // toute la durée de vie restante du jeton (jusqu'à 1h) si aucun 401 fortuit ne
                    // survient entre-temps pour déclencher le refresh normal de TokenAuthenticator.
                    refreshScopesIfMissing(active)
                }
                else -> {
                    // Connexion créée AVANT ce correctif : jeton expiré et pas de clé API
                    // conservée → on demande une reconnexion (une seule fois ; ensuite la clé
                    // est gardée et la session ne sera plus jamais perdue).
                    refreshConnections()
                    _authState.value = AuthState.Unauthenticated
                }
            }
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
                            apiKey = apiKey.trim(),
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
                // Aucune entité Room n'est cloisonnée par boutique : purge D'ABORD (sinon l'UI
                // afficherait un instant les données de l'ancienne boutique sous l'identité de
                // la nouvelle), PUIS active la nouvelle boutique — le flux normal se charge de
                // rafraîchir ses données.
                localCacheStore.clearAll()
                activate(connection)
            }
        }

        override suspend fun removeConnection(id: String) {
            withContext(ioDispatcher) {
                val wasActive = connectionStore.getActiveId() == id
                val remaining = connectionStore.read().filterNot { it.id == id }
                connectionStore.write(remaining)
                localCacheStore.clearAll()
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
                // Évite que des PII (noms, emails, historiques clients) restent en clair dans
                // prestaflow.db après déconnexion.
                localCacheStore.clearAll()
            }
            _connections.value = emptyList()
            _authState.value = AuthState.Unauthenticated
        }

        override suspend fun getActiveToken(): AuthToken? =
            withContext(ioDispatcher) {
                tokenManager.currentToken()?.takeUnless { it.isExpired }
            }

        override suspend fun refreshActiveToken(): Boolean =
            withContext(ioDispatcher) {
                refreshMutex.withLock {
                    val active = activeConnection() ?: return@withLock false
                    val apiKey = active.apiKey.ifBlank { return@withLock false }
                    when (val outcome = authenticate(active.shopUrl, apiKey)) {
                        is AuthOutcome.Success -> {
                            val refreshed = active.copy(token = outcome.token)
                            val updated =
                                connectionStore.read().map { if (it.id == refreshed.id) refreshed else it }
                            connectionStore.write(updated)
                            activate(refreshed)
                            Timber.i("Jeton rafraîchi pour la boutique active %s", active.shopUrl)
                            true
                        }
                        is AuthOutcome.Failure -> {
                            Timber.w("Échec du rafraîchissement du jeton pour %s", active.shopUrl)
                            false
                        }
                    }
                }
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

            // Le login s'exécute via un client HTTP DÉDIÉ (LoginApiClient), jamais via le
            // client OkHttp partagé : celui-ci porte DynamicBaseUrlInterceptor/AuthInterceptor
            // qui routeraient TOUTE requête (y compris une requête concurrente en cours pendant
            // cette fenêtre de login) vers la boutique ACTIVE avec son Bearer. Le routage global
            // (endpointManager.setActiveBaseUrl) n'est basculé qu'APRÈS un login réussi, dans
            // activate() — jamais avant, pour ne jamais faire fuiter le JWT de la boutique déjà
            // active vers cet hôte candidat non encore authentifié.
            val response =
                runCatching {
                    withContext(ioDispatcher) {
                        loginApiClient.login(apiBaseUrl, AuthRequestDto(apiKey = apiKey.trim(), shopUrl = normalizedUrl))
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
                    // Toute IOException (DNS, timeout, refus de connexion…) = hôte injoignable.
                    AuthFailure.HostUnreachable(message)
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
                    // HTTP 404 sur l'endpoint du connecteur = module absent.
                    if (error.code() == HTTP_NOT_FOUND) {
                        AuthFailure.ModuleNotInstalled
                    } else {
                        AuthFailure.Network(message)
                    }
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

        /**
         * Renouvelle silencieusement le jeton d'une connexion dont les scopes sont vides —
         * signature d'une connexion persistée AVANT l'introduction du filtrage par scope côté app
         * (`ShopConnectionStore.Stored.scopes` absent du JSON legacy, décodé à `emptyList()` par
         * défaut). Un jeton authentique ne porte JAMAIS un tableau de scopes vide : le connecteur
         * applique toujours des scopes par défaut (`SettingsService::DEFAULT_SCOPES`, cf.
         * `rebuild-connector`) — `scopes.isEmpty()` signale donc sans ambiguïté ce cas legacy,
         * jamais une session normale.
         *
         * Sans ce correctif, l'utilisatrice concernée verrait le SAV disparaître de l'onglet
         * Clients sans explication alors qu'elle y a parfaitement droit (capacité toujours vraie,
         * scope simplement jamais persisté) — et ça durerait potentiellement jusqu'à l'expiration
         * du jeton (TTL ~1h côté module), le seul déclencheur normal d'un renouvellement
         * ([TokenAuthenticator], sur un 401). On appelle donc directement [refreshActiveToken] —
         * même mécanisme, déclenché au démarrage plutôt qu'en réaction à un 401 — qui effectue un
         * VRAI re-login via la clé API conservée et republie [connection] avec les scopes réels.
         *
         * Best-effort et silencieux : un échec (ex. hors-ligne au démarrage) laisse les scopes
         * vides pour cette session — SAV/Avis restent masqués (fail-closed, cf.
         * `ClientsSection.visibleSections`) plutôt que de bloquer le démarrage ou de redemander une
         * reconnexion complète ; le prochain démarrage — ou le prochain 401 — retentera.
         */
        private fun refreshScopesIfMissing(connection: ShopConnection) {
            if (connection.token.scopes.isNotEmpty()) return
            if (connection.apiKey.isBlank()) return // pas de reconnexion silencieuse possible sans clé API
            scope.launch {
                Timber.i(
                    "Connexion %s sans scopes persistés (antérieure à leur introduction) — renouvellement silencieux",
                    connection.shopUrl,
                )
                refreshActiveToken()
            }
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
            const val HTTP_NOT_FOUND = 404
        }
    }
