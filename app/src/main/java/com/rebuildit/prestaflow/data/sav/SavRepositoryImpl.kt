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

@Singleton
class SavRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : SavRepository {
        private val _toProcessCount = MutableStateFlow(0)
        override val toProcessCount: Flow<Int> = _toProcessCount

        /**
         * `GET /sav/stats` (v1.20.0+) — compteur exact calculé en SQL côté connecteur, indépendant
         * de la pagination. Remplace l'ancienne approximation par scan d'une page de `GET /sav`
         * comptant les `unread` : cette dernière produisait des chiffres sans rapport avec la
         * réalité (449 fils « non lus » mesurés en prod sur cette même boutique, contre 2 fils
         * réellement « à traiter » — cf. Javadoc de [com.rebuildit.prestaflow.domain.sav.SavRepository.toProcessCount]).
         * Best-effort : un échec réseau conserve la dernière valeur connue.
         */
        override suspend fun refreshToProcessCount() {
            withContext(ioDispatcher) {
                runCatching { api.getSavStats() }
                    .onSuccess { response ->
                        _toProcessCount.value = response.toProcess
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
