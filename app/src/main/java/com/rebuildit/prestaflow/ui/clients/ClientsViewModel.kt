package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.core.network.NetworkErrorMapper
import com.rebuildit.prestaflow.core.ui.UiText
import com.rebuildit.prestaflow.domain.clients.ClientsRepository
import com.rebuildit.prestaflow.domain.clients.model.Client
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TOP_CLIENTS_LIMIT = 20

@HiltViewModel
class ClientsViewModel @Inject constructor(
    private val clientsRepository: ClientsRepository,
    private val networkErrorMapper: NetworkErrorMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

    init {
        observeClients()
        refresh(forceRemote = true, notifyOnError = false)
    }

    fun onRefresh() {
        refresh(forceRemote = true, notifyOnError = true)
    }

    private fun observeClients() {
        viewModelScope.launch {
            clientsRepository.observeTopClients(TOP_CLIENTS_LIMIT).collect { clients ->
                _uiState.update { current ->
                    current.copy(
                        clients = clients,
                        isLoading = false,
                        isRefreshing = false,
                        error = if (clients.isNotEmpty()) null else current.error
                    )
                }
            }
        }
    }

    private fun refresh(forceRemote: Boolean, notifyOnError: Boolean) {
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isRefreshing = true,
                    isLoading = current.clients.isEmpty(),
                    error = if (notifyOnError) null else current.error
                )
            }

            runCatching { clientsRepository.refreshTopClients(TOP_CLIENTS_LIMIT, forceRemote) }
                .onFailure { error ->
                    Timber.w(error, "Failed to refresh clients")
                    _uiState.update { current ->
                        val mapped = networkErrorMapper.map(error)
                        current.copy(
                            isRefreshing = false,
                            isLoading = current.clients.isEmpty(),
                            error = if (notifyOnError) mapped else current.error
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            isLoading = current.clients.isEmpty(),
                            error = null
                        )
                    }
                }
        }
    }
}

data class ClientsUiState(
    val clients: List<Client> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
)
