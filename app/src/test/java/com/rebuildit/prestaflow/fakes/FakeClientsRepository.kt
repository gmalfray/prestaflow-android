package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import com.rebuildit.prestaflow.domain.clients.model.ClientsPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake en mémoire de [ClientsRepository].
 *
 * - [setClients] pousse une liste dans le flux observable (top clients Room).
 * - [fetchStatsResult] : valeur renvoyée par [fetchStats] (null pour simuler une erreur).
 * - [shouldThrowOnRefresh] : force un échec sur [refreshTopClients].
 * - [fetchClientsResult] : résultat renvoyé par [fetchClients] (configurable par test).
 * - [shouldThrowOnFetchClients] : force un échec sur [fetchClients].
 */
class FakeClientsRepository : ClientsRepository {
    private val _clientsFlow = MutableStateFlow<List<Client>>(emptyList())

    fun setClients(clients: List<Client>) {
        _clientsFlow.value = clients
    }

    var fetchStatsResult: ClientStats? = ClientStats(total = 150, newThisMonth = 12)
    var shouldThrowOnRefresh = false

    var fetchClientsResult: ClientsPage =
        ClientsPage(
            clients = emptyList(),
            hasNext = false,
            nextOffset = 0,
        )
    var shouldThrowOnFetchClients = false

    /** Dernier appel reçu par [fetchClients] (pour assertions). */
    var lastFetchClientsCall: FetchClientsCall? = null

    override fun observeTopClients(limit: Int): Flow<List<Client>> = _clientsFlow.asStateFlow()

    override suspend fun refreshTopClients(
        limit: Int,
        forceRemote: Boolean,
    ) {
        if (shouldThrowOnRefresh) throw RuntimeException("Erreur réseau simulée")
    }

    override fun observeClient(clientId: Long): Flow<Client?> = MutableStateFlow(_clientsFlow.value.find { it.id == clientId })

    override suspend fun refreshClient(
        clientId: Long,
        forceRemote: Boolean,
    ) = Unit

    override suspend fun fetchStats(): ClientStats? = fetchStatsResult

    override suspend fun fetchClients(
        query: String?,
        sort: String?,
        createdFrom: String?,
        createdTo: String?,
        limit: Int,
        offset: Int,
    ): ClientsPage {
        lastFetchClientsCall =
            FetchClientsCall(
                query = query,
                sort = sort,
                createdFrom = createdFrom,
                createdTo = createdTo,
                limit = limit,
                offset = offset,
            )
        if (shouldThrowOnFetchClients) throw RuntimeException("Erreur réseau fetchClients simulée")
        return fetchClientsResult
    }

    data class FetchClientsCall(
        val query: String?,
        val sort: String?,
        val createdFrom: String?,
        val createdTo: String?,
        val limit: Int,
        val offset: Int,
    )
}
