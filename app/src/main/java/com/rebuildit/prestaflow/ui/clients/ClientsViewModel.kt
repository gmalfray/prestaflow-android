package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.clients.model.ClientStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val TOP_CLIENTS_LIMIT = 20

@HiltViewModel
class ClientsViewModel
    @Inject
    constructor(
        private val clientsRepository: ClientsRepository,
        private val networkErrorMapper: NetworkErrorMapper,
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ClientsUiState())
        val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

        init {
            observeClients()
            refresh(forceRemote = true, notifyOnError = false)
            observeActiveShopSwitch()
        }

        fun onRefresh() {
            refresh(forceRemote = true, notifyOnError = true)
        }

        fun onQueryChange(query: String) {
            _uiState.update { it.copy(query = query) }
        }

        /**
         * Active ou désactive un filtre de carte sur la liste des clients.
         * Un tap sur la carte déjà active la désélectionne (retour à ALL).
         */
        fun onFilterChange(filter: ClientFilter) {
            _uiState.update { current ->
                val newFilter = if (current.activeFilter == filter) ClientFilter.ALL else filter
                current.copy(activeFilter = newFilter)
            }
        }

        private fun observeActiveShopSwitch() {
            viewModelScope.launch {
                authRepository.connections
                    .map { list -> list.firstOrNull { it.isActive }?.id }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        _uiState.update { current ->
                            current.copy(clients = emptyList(), stats = null, isLoading = true, error = null)
                        }
                        refresh(forceRemote = true, notifyOnError = true)
                    }
            }
        }

        private fun observeClients() {
            viewModelScope.launch {
                clientsRepository.observeTopClients(TOP_CLIENTS_LIMIT).collect { clients ->
                    _uiState.update { current ->
                        current.copy(
                            clients = clients,
                            isLoading = false,
                            isRefreshing = false,
                            error = if (clients.isNotEmpty()) null else current.error,
                        )
                    }
                }
            }
        }

        private fun refresh(
            forceRemote: Boolean,
            notifyOnError: Boolean,
        ) {
            viewModelScope.launch {
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = true,
                        isLoading = current.clients.isEmpty(),
                        error = if (notifyOnError) null else current.error,
                    )
                }

                // Les deux appels sont lancés en parallèle pour minimiser la latence.
                val topClientsResult =
                    runCatching { clientsRepository.refreshTopClients(TOP_CLIENTS_LIMIT, forceRemote) }
                val stats = clientsRepository.fetchStats()

                topClientsResult
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh clients")
                        _uiState.update { current ->
                            val mapped = networkErrorMapper.map(error)
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.clients.isEmpty(),
                                error = if (notifyOnError) mapped else current.error,
                                stats = stats ?: current.stats,
                            )
                        }
                    }
                    .onSuccess {
                        _uiState.update { current ->
                            current.copy(
                                isRefreshing = false,
                                isLoading = current.clients.isEmpty(),
                                error = null,
                                stats = stats ?: current.stats,
                            )
                        }
                    }
            }
        }
    }

/**
 * Filtre actif sur la liste des clients de l'écran Clients.
 *
 * [NEW_THIS_MONTH] : visuellement sélectionné, mais NE filtre PAS réellement la liste :
 * le DTO [com.rebuildit.prestaflow.data.remote.dto.CustomerDto] (endpoint `/customers/top`)
 * ne contient pas de champ `date_add` (date d'inscription). Pour activer un filtrage réel,
 * le connecteur doit exposer `date_add` dans la réponse de la liste clients.
 */
enum class ClientFilter { ALL, NEW_THIS_MONTH }

data class ClientsUiState(
    val clients: List<Client> = emptyList(),
    /**
     * Statistiques agrégées depuis [GET customers/stats].
     * Null tant que la requête n'a pas abouti (état initial ou erreur).
     */
    val stats: ClientStats? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val query: String = "",
    /** Filtre actif via les cartes KPI en haut de l'écran. */
    val activeFilter: ClientFilter = ClientFilter.ALL,
) {
    /**
     * Liste filtrée par [query] sur le nom complet et l'e-mail (insensible à la casse).
     *
     * Note : le filtre [ClientFilter.NEW_THIS_MONTH] ne réduit pas encore la liste
     * car `date_add` est absent du DTO customer (voir [ClientFilter]).
     */
    val visibleClients: List<Client>
        get() =
            if (query.isBlank()) {
                clients
            } else {
                clients.filter {
                    it.fullName.contains(query, ignoreCase = true) ||
                        it.email.contains(query, ignoreCase = true)
                }
            }
}
