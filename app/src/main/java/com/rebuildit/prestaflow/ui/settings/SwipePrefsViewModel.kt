package com.rebuildit.prestaflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.orders.OrdersPreferencesRepository
import com.rebuildit.prestaflow.domain.orders.OrdersRepository
import com.rebuildit.prestaflow.domain.orders.model.OrderStatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SwipePrefsUiState(
    val swipeEnabled: Boolean = true,
    val swipeSourceStatusId: Int? = null,
    val swipeLeftTargetStatusId: Int? = null,
    val swipeRightTargetStatusId: Int? = null,
    /** Statuts disponibles dans la boutique active. */
    val availableStatuses: List<OrderStatusFilter> = emptyList(),
    val isLoadingStatuses: Boolean = false,
    /** true si le dernier chargement des statuts a échoué (permet d'afficher une erreur + Réessayer). */
    val statusesError: Boolean = false,
)

/** État interne du chargement des statuts, regroupé pour une émission atomique dans le combine. */
private data class StatusesLoadState(
    val statuses: List<OrderStatusFilter> = emptyList(),
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)

@HiltViewModel
class SwipePrefsViewModel
    @Inject
    constructor(
        private val ordersPreferencesRepository: OrdersPreferencesRepository,
        private val ordersRepository: OrdersRepository,
    ) : ViewModel() {
        // État de chargement des statuts regroupé (liste + loading + erreur) dans UN seul flow,
        // pour que le combine ré-émette bien à chaque changement (loading/erreur inclus).
        private val statusesLoadState = MutableStateFlow(StatusesLoadState())

        val uiState: StateFlow<SwipePrefsUiState> =
            combine(
                ordersPreferencesRepository.swipeEnabled,
                ordersPreferencesRepository.swipeSourceStatusId,
                ordersPreferencesRepository.swipeLeftTargetStatusId,
                ordersPreferencesRepository.swipeRightTargetStatusId,
                statusesLoadState,
            ) { enabled, sourceId, leftId, rightId, load ->
                SwipePrefsUiState(
                    swipeEnabled = enabled,
                    swipeSourceStatusId = sourceId,
                    swipeLeftTargetStatusId = leftId,
                    swipeRightTargetStatusId = rightId,
                    availableStatuses = load.statuses,
                    isLoadingStatuses = load.isLoading,
                    statusesError = load.isError,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SwipePrefsUiState(),
            )

        init {
            loadStatuses()
        }

        /** Charge (ou recharge, pour le bouton Réessayer) la liste des statuts. */
        fun loadStatuses() {
            viewModelScope.launch {
                statusesLoadState.update { it.copy(isLoading = true, isError = false) }
                runCatching { ordersRepository.getOrderStatuses() }
                    .onSuccess { statuses ->
                        statusesLoadState.value = StatusesLoadState(statuses = statuses, isLoading = false, isError = false)
                    }
                    .onFailure { error ->
                        Timber.w(error, "Impossible de charger les statuts pour les préférences swipe")
                        statusesLoadState.update { it.copy(isLoading = false, isError = true) }
                    }
            }
        }

        fun setSwipeEnabled(enabled: Boolean) {
            viewModelScope.launch {
                ordersPreferencesRepository.setSwipeEnabled(enabled)
            }
        }

        fun setSwipeSourceStatusId(id: Int?) {
            viewModelScope.launch {
                ordersPreferencesRepository.setSwipeSourceStatusId(id)
            }
        }

        fun setSwipeLeftTargetStatusId(id: Int?) {
            viewModelScope.launch {
                ordersPreferencesRepository.setSwipeLeftTargetStatusId(id)
            }
        }

        fun setSwipeRightTargetStatusId(id: Int?) {
            viewModelScope.launch {
                ordersPreferencesRepository.setSwipeRightTargetStatusId(id)
            }
        }
    }
