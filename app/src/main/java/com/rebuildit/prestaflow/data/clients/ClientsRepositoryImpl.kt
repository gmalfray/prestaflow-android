package com.rebuildit.prestaflow.data.clients

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.clients.mapper.toDomain
import com.rebuildit.prestaflow.data.clients.mapper.toEntity
import com.rebuildit.prestaflow.data.local.dao.ClientDao
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientsRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val clientDao: ClientDao,
        private val networkErrorMapper: NetworkErrorMapper,
        private val ioDispatcher: CoroutineDispatcher,
    ) : ClientsRepository {
        override fun observeTopClients(limit: Int): Flow<List<Client>> =
            clientDao.observeTopClients(limit).map { entities -> entities.map { it.toDomain() } }

        override suspend fun refreshTopClients(
            limit: Int,
            forceRemote: Boolean,
        ) {
            withContext(ioDispatcher) {
                val result = runCatching { api.getTopCustomers(limit) }
                result.fold(
                    onSuccess = { payload ->
                        val timestamp = java.time.Instant.now().toString()
                        val entities = payload.customers.map { it.toEntity(timestamp) }
                        clientDao.clearAll()
                        clientDao.upsertAll(entities)
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                    },
                )
            }
        }

        override fun observeClient(clientId: Long): Flow<Client?> = clientDao.observeClient(clientId).map { it?.toDomain() }

        override suspend fun refreshClient(
            clientId: Long,
            forceRemote: Boolean,
        ) {
            withContext(ioDispatcher) {
                val result = runCatching { api.getCustomer(clientId) }
                result.fold(
                    onSuccess = { response ->
                        val timestamp = java.time.Instant.now().toString()
                        val entity = response.customer.toEntity(timestamp)
                        clientDao.upsert(entity)
                    },
                    onFailure = { error ->
                        Timber.w(networkErrorMapper.map(error).toString())
                        if (forceRemote) throw error
                    },
                )
            }
        }

        override suspend fun fetchStats(): ClientStats? =
            withContext(ioDispatcher) {
                runCatching { api.getCustomerStats() }
                    .fold(
                        onSuccess = { dto ->
                            ClientStats(total = dto.total, newThisMonth = dto.newThisMonth)
                        },
                        onFailure = { error ->
                            Timber.w(networkErrorMapper.map(error).toString())
                            null
                        },
                    )
            }
    }
