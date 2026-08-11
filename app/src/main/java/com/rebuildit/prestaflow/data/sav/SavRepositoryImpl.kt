package com.rebuildit.prestaflow.data.sav

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.SavReplyRequestDto
import com.rebuildit.prestaflow.data.remote.dto.SavStatusUpdateRequestDto
import com.rebuildit.prestaflow.data.sav.mapper.toDomain
import com.rebuildit.prestaflow.domain.sav.SavRepository
import com.rebuildit.prestaflow.domain.sav.model.SavReplyResult
import com.rebuildit.prestaflow.domain.sav.model.SavThreadDetail
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.domain.sav.model.SavThreadsPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nombre max de fils inspectés pour approximer [SavRepositoryImpl.unreadThreadCount] — le max
 * accepté par le connecteur (`limit`, cf. `GET /sav`).
 */
private const val UNREAD_COUNT_SCAN_LIMIT = 100

@Singleton
class SavRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : SavRepository {
        private val _unreadThreadCount = MutableStateFlow(0)
        override val unreadThreadCount: Flow<Int> = _unreadThreadCount

        /**
         * Le connecteur n'expose pas de compteur dédié (cf. `rebuild-connector` docs/api.md § SAV) :
         * on approxime en comptant les fils `unread` dans la première page de fils non-clos (tri
         * par défaut du connecteur = non-clos d'abord). Avec 97 fils ouverts mesurés en prod (étude
         * `rebuild-it/docs/app-avis-sav.md` § « Ce que disent les données »), une page de
         * [UNREAD_COUNT_SCAN_LIMIT] suffit aujourd'hui. Si ce nombre venait à dépasser la limite,
         * le compteur SOUS-ESTIMERAIT plutôt que d'enchaîner des pages supplémentaires en tâche de
         * fond — coût réseau non justifié pour une simple pastille.
         */
        override suspend fun refreshUnreadCount() {
            withContext(ioDispatcher) {
                runCatching { api.getSavThreads(mapOf("limit" to UNREAD_COUNT_SCAN_LIMIT.toString())) }
                    .onSuccess { response ->
                        _unreadThreadCount.value = response.threads.count { it.unread }
                    }
                    .onFailure { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                    }
            }
        }

        override suspend fun fetchThreads(
            status: SavThreadStatus?,
            limit: Int,
            offset: Int,
        ): SavThreadsPage =
            withContext(ioDispatcher) {
                val filters =
                    buildMap {
                        put("limit", limit.toString())
                        put("offset", offset.toString())
                        if (status != null) put("status", status.apiValue)
                    }
                val response =
                    runCatching { api.getSavThreads(filters) }.getOrElse { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    }
                val pagination = response.pagination
                SavThreadsPage(
                    threads = response.threads.map { it.toDomain() },
                    hasNext = pagination?.hasNext == true,
                    nextOffset = pagination?.nextOffset ?: (offset + response.threads.size),
                )
            }

        override suspend fun fetchThread(threadId: Long): SavThreadDetail =
            withContext(ioDispatcher) {
                val response =
                    runCatching { api.getSavThread(threadId) }.getOrElse { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    }
                SavThreadDetail(
                    thread = response.thread.toDomain(),
                    messages = response.messages.map { it.toDomain() },
                )
            }

        override suspend fun updateThreadStatus(
            threadId: Long,
            status: SavThreadStatus,
        ) {
            withContext(ioDispatcher) {
                runCatching {
                    api.updateSavThreadStatus(threadId, SavStatusUpdateRequestDto(status = status.apiValue))
                }.onFailure { error ->
                    Timber.w(networkErrorMapper.map(error).toString())
                    throw error
                }
            }
        }

        override suspend fun replyToThread(
            threadId: Long,
            message: String,
        ): SavReplyResult =
            withContext(ioDispatcher) {
                val response =
                    runCatching {
                        api.replySavThread(threadId, SavReplyRequestDto(message = message))
                    }.getOrElse { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        throw error
                    }
                SavReplyResult(
                    thread = response.thread.toDomain(),
                    message = response.message.toDomain(),
                    emailSent = response.emailSent,
                )
            }
    }
