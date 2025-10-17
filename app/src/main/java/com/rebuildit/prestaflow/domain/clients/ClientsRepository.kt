package com.rebuildit.prestaflow.domain.clients

import com.rebuildit.prestaflow.domain.clients.model.Client
import kotlinx.coroutines.flow.Flow

interface ClientsRepository {
    fun observeTopClients(limit: Int = 10): Flow<List<Client>>
    suspend fun refreshTopClients(limit: Int = 10, forceRemote: Boolean = false)
}
