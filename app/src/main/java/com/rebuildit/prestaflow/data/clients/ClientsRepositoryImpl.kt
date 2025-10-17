package com.rebuildit.prestaflow.data.clients

import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.data.clients.mapper.toDomain
import com.rebuildit.prestaflow.data.clients.mapper.toEntity
import com.rebuildit.prestaflow.data.local.dao.ClientDao
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class ClientsRepositoryImpl @Inject constructor(
    private val api: PrestaFlowApi,
    private val clientDao: ClientDao,
    private val networkErrorMapper: NetworkErrorMapper,
    private val ioDispatcher: CoroutineDispatcher
) : ClientsRepository {

    override fun observeTopClients(limit: Int): Flow<List<Client>> =
        clientDao.observeTopClients(limit).map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshTopClients(limit: Int, forceRemote: Boolean) {
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
                }
            )
        }
    }
}
