package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake en mémoire de [ClientsRepository].
 *
 * - [setClients] pousse une liste dans le flux observable.
 * - [fetchStatsResult] : valeur renvoyée par [fetchStats] (null pour simuler une erreur).
 * - [shouldThrowOnRefresh] : force un échec sur [refreshTopClients].
 */
class FakeClientsRepository : ClientsRepository {

    private val _clientsFlow = MutableStateFlow<List<Client>>(emptyList())

    fun setClients(clients: List<Client>) {
        _clientsFlow.value = clients
    }

    var fetchStatsResult: ClientStats? = ClientStats(total = 150, newThisMonth = 12)
    var shouldThrowOnRefresh = false

    override fun observeTopClients(limit: Int): Flow<List<Client>> = _clientsFlow.asStateFlow()

    override suspend fun refreshTopClients(limit: Int, forceRemote: Boolean) {
        if (shouldThrowOnRefresh) throw RuntimeException("Erreur réseau simulée")
    }

    override fun observeClient(clientId: Long): Flow<Client?> =
        MutableStateFlow(_clientsFlow.value.find { it.id == clientId })

    override suspend fun refreshClient(clientId: Long, forceRemote: Boolean) = Unit

    override suspend fun fetchStats(): ClientStats? = fetchStatsResult
}
