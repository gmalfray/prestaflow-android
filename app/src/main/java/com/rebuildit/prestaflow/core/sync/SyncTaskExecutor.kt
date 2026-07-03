package com.rebuildit.prestaflow.core.sync

import androidx.work.ListenableWorker.Result
import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.core.sync.ConflictResolution.Drop
import com.rebuildit.prestaflow.core.sync.ConflictResolution.Hold
import com.rebuildit.prestaflow.core.sync.ConflictResolution.Retry
import com.rebuildit.prestaflow.data.auth.LoginApiClientContract
import com.rebuildit.prestaflow.data.remote.dto.AuthRequestDto
import com.rebuildit.prestaflow.di.SyncHttpClient
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.sync.SyncQueueRepository
import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_CONFLICT = 409
private const val HTTP_SERVER_ERROR_MIN = 500
private const val HTTP_SERVER_ERROR_MAX = 599

/**
 * Exécute une tâche [PendingSyncTask] unique : résolution de la boutique cible (celle
 * **stockée sur la tâche**, pas la boutique active courante), requête HTTP, interprétation
 * de la réponse. Extrait de [SyncWorker] pour permettre un test JVM pur (sans WorkManager).
 *
 * Utilise systématiquement le client [SyncHttpClient] dédié (jamais le client OkHttp partagé
 * `DynamicBaseUrlInterceptor`/`AuthInterceptor`, qui router aient toute requête vers la
 * boutique ACTIVE) et pose lui-même le Bearer de la boutique de la tâche.
 */
@Singleton
@Suppress("LongParameterList") // Dépendances réseau/sécurité/queue toutes nécessaires au routage par tâche
class SyncTaskExecutor
    @Inject
    constructor(
        private val endpointManager: ApiEndpointManager,
        private val connectionStore: ShopConnectionStore,
        private val loginApiClient: LoginApiClientContract,
        @SyncHttpClient private val httpClient: OkHttpClient,
        private val syncQueueRepository: SyncQueueRepository,
        private val conflictResolver: SyncConflictResolver,
    ) {
        @Suppress("ReturnCount") // Sorties anticipées : boutique inconnue / URL invalide / réponse traitée
        suspend fun execute(task: PendingSyncTask): Result {
            val connection = connectionStore.read().firstOrNull { it.id == task.shopUrl }
            if (connection == null) {
                Timber.w("Abandon de la tâche ${task.id} : boutique '${task.shopUrl}' inconnue ou déconnectée")
                syncQueueRepository.remove(task.id)
                return Result.success()
            }
            val apiBaseUrl = endpointManager.buildApiBaseUrl(task.shopUrl)
            if (apiBaseUrl == null) {
                Timber.w("Abandon de la tâche ${task.id} : URL de boutique invalide '${task.shopUrl}'")
                syncQueueRepository.remove(task.id)
                return Result.success()
            }

            val token = resolveToken(connection, apiBaseUrl)
            val request = buildRequest(apiBaseUrl, task, token)
            val now = Instant.now().toString()
            val response = runCatching { httpClient.newCall(request).execute() }
            syncQueueRepository.markAttempt(task.id, now)
            return response.fold(
                onSuccess = { httpResponse -> handleResponse(task, httpResponse) },
                onFailure = { error ->
                    Timber.w(error, "Failed to execute sync task ${task.id}")
                    Result.retry()
                },
            )
        }

        private suspend fun handleResponse(
            task: PendingSyncTask,
            httpResponse: okhttp3.Response,
        ): Result =
            httpResponse.use { resp ->
                if (resp.isSuccessful) {
                    syncQueueRepository.remove(task.id)
                    Result.success()
                } else if (resp.code == HTTP_CONFLICT) {
                    val body = resp.body?.string()
                    when (val resolution = conflictResolver.resolve(task, resp.code, body)) {
                        Drop -> {
                            syncQueueRepository.remove(task.id)
                            Result.success()
                        }
                        Retry -> Result.retry()
                        is Hold -> {
                            Timber.w("Holding task ${task.id}: ${resolution.reason}")
                            Result.success()
                        }
                    }
                } else if (resp.code in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX) {
                    Timber.w("Server error ${resp.code} for ${task.endpoint}")
                    Result.retry()
                } else {
                    Timber.w("Dropping task ${task.id} after response ${resp.code}")
                    syncQueueRepository.remove(task.id)
                    Result.success()
                }
            }

        /**
         * Retourne un jeton valide pour [connection]. Si le jeton connu est expiré et qu'une
         * clé API est disponible, tente un re-login *best-effort* pour CETTE boutique précise
         * (jamais via le client partagé). En cas d'échec, retourne le jeton existant tel quel :
         * la requête échouera proprement (401), et la tâche sera abandonnée avec trace Timber
         * par [handleResponse] plutôt que rejouée indéfiniment.
         */
        private suspend fun resolveToken(
            connection: ShopConnection,
            apiBaseUrl: HttpUrl,
        ): String {
            if (!connection.token.isExpired || connection.apiKey.isBlank()) {
                return connection.token.value
            }
            return runCatching {
                loginApiClient.login(
                    apiBaseUrl,
                    AuthRequestDto(apiKey = connection.apiKey, shopUrl = connection.shopUrl),
                )
            }.onSuccess {
                Timber.i("Jeton rafraîchi pour '${connection.shopUrl}' avant relecture de la file offline")
            }.onFailure {
                Timber.w(it, "Échec du rafraîchissement du jeton pour '${connection.shopUrl}', tentative avec le jeton existant")
            }.getOrNull()?.token ?: connection.token.value
        }

        private fun buildRequest(
            apiBaseUrl: HttpUrl,
            task: PendingSyncTask,
            bearerToken: String,
        ): Request {
            val method = task.method.uppercase()
            val baseUrl = apiBaseUrl.toString().trimEnd('/')
            val url = baseUrl + "/" + task.endpoint.trimStart('/')
            val body =
                when (method) {
                    "POST", "PUT", "PATCH" ->
                        task.payloadJson.takeIf { it.isNotBlank() }?.toJsonRequestBody()
                            ?: "{}".toJsonRequestBody()
                    else -> null
                }
            return Request.Builder()
                .url(url)
                .method(method, body)
                .header("Authorization", "Bearer $bearerToken")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
        }

        private fun String.toJsonRequestBody(): RequestBody = this.toRequestBody("application/json".toMediaType())
    }
