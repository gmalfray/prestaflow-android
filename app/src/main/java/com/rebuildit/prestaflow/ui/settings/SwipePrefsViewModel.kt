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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SwipePrefsUiState(
    val swipeEnabled: Boolean = true,
    val swipeSourceStatusId: Int? = null,
    val swipeLeftTargetStatusId: Int? = null,
    val swipeRightTargetStatusId: Int? = null,
    /** Statuts disponibles dans la boutique active (chargés une seule fois). */
    val availableStatuses: List<OrderStatusFilter> = emptyList(),
    val isLoadingStatuses: Boolean = false,
)

@HiltViewModel
class SwipePrefsViewModel
    @Inject
    constructor(
        private val ordersPreferencesRepository: OrdersPreferencesRepository,
        private val ordersRepository: OrdersRepository,
    ) : ViewModel() {
        private val _availableStatuses = MutableStateFlow<List<OrderStatusFilter>>(emptyList())
        private val _isLoadingStatuses = MutableStateFlow(false)

        val uiState: StateFlow<SwipePrefsUiState> =
            combine(
                ordersPreferencesRepository.swipeEnabled,
                ordersPreferencesRepository.swipeSourceStatusId,
                ordersPreferencesRepository.swipeLeftTargetStatusId,
                ordersPreferencesRepository.swipeRightTargetStatusId,
                _availableStatuses,
            ) { enabled, sourceId, leftId, rightId, statuses ->
                SwipePrefsUiState(
                    swipeEnabled = enabled,
                    swipeSourceStatusId = sourceId,
                    swipeLeftTargetStatusId = leftId,
                    swipeRightTargetStatusId = rightId,
                    availableStatuses = statuses,
                    isLoadingStatuses = _isLoadingStatuses.value,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SwipePrefsUiState(),
            )

        init {
            loadStatuses()
        }

        private fun loadStatuses() {
            viewModelScope.launch {
                _isLoadingStatuses.value = true
                runCatching { ordersRepository.getOrderStatuses() }
                    .onSuccess { statuses ->
                        _availableStatuses.value = statuses
                    }
                    .onFailure { error ->
                        Timber.w(error, "Impossible de charger les statuts pour les préférences swipe")
                    }
                _isLoadingStatuses.value = false
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
