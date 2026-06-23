package com.rebuildit.prestaflow.domain.clients

import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import kotlinx.coroutines.flow.Flow

interface ClientsRepository {
    fun observeTopClients(limit: Int = 10): Flow<List<Client>>

    suspend fun refreshTopClients(
        limit: Int = 10,
        forceRemote: Boolean = false,
    )

    fun observeClient(clientId: Long): Flow<Client?>

    suspend fun refreshClient(
        clientId: Long,
        forceRemote: Boolean = false,
    )

    /**
     * Récupère les statistiques agrégées des clients depuis [GET customers/stats].
     * @return [ClientStats] avec le total et les nouveaux du mois, ou null en cas d'erreur réseau.
     */
    suspend fun fetchStats(): ClientStats?
}
