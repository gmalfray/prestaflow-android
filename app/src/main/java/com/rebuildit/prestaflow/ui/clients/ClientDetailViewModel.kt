package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ClientDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val clientsRepository: ClientsRepository,
        private val ordersRepository: OrdersRepository,
        private val networkErrorMapper: NetworkErrorMapper,
    ) : ViewModel() {
        private val clientId: Long = checkNotNull(savedStateHandle["clientId"])

        private val _uiState = MutableStateFlow(ClientDetailUiState())
        val uiState: StateFlow<ClientDetailUiState> = _uiState.asStateFlow()

        init {
            observeClient()
            refreshClient()
            loadStatuses()
        }

        private fun observeClient() {
            viewModelScope.launch {
                clientsRepository.observeClient(clientId).collect { client ->
                    _uiState.update { it.copy(client = client, isLoading = false) }
                }
            }
        }

        private fun refreshClient() {
            viewModelScope.launch {
                runCatching { clientsRepository.refreshClient(clientId, forceRemote = true) }
                    .onFailure { error ->
                        Timber.w(error, "Failed to refresh client $clientId")
                        _uiState.update { it.copy(error = networkErrorMapper.map(error)) }
                    }
            }
        }

        /**
         * Charge les statuts disponibles pour résoudre les couleurs des badges.
         * En cas d'échec, la liste reste vide et le fallback heuristique de [OrderStatusBadge] s'applique.
         */
        private fun loadStatuses() {
            viewModelScope.launch {
                runCatching { ordersRepository.getOrderStatuses() }
                    .onSuccess { statuses ->
                        _uiState.update { it.copy(availableStatuses = statuses) }
                    }
                    .onFailure { error ->
                        Timber.w(error, "Failed to load order statuses for client detail badges")
                        // Pas de propagation d'erreur : le fallback heuristique prend le relai
                    }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }

data class ClientDetailUiState(
    val client: Client? = null,
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val availableStatuses: List<OrderStatusFilter> = emptyList(),
)
