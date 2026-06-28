package com.rebuildit.prestaflow.domain.clients

import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import com.rebuildit.prestaflow.domain.clients.model.ClientsPage
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

    /**
     * Charge une page de clients depuis `GET /customers`.
     *
     * @param query Recherche full-text (nom/email). Null ou vide = pas de filtre texte.
     * @param sort Critère de tri (ex. `date_desc`). Null = défaut serveur.
     * @param createdFrom Borne inférieure de `date_add` (format `YYYY-MM-DD`). Null = pas de filtre date début.
     * @param createdTo Borne supérieure de `date_add` (format `YYYY-MM-DD`). Null = pas de filtre date fin.
     * @param limit Nombre de clients par page.
     * @param offset Décalage pour la pagination.
     * @return [ClientsPage] avec la liste et les métadonnées de pagination.
     */
    suspend fun fetchClients(
        query: String? = null,
        sort: String? = null,
        createdFrom: String? = null,
        createdTo: String? = null,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): ClientsPage

    companion object {
        const val PAGE_SIZE = 20
    }
}
