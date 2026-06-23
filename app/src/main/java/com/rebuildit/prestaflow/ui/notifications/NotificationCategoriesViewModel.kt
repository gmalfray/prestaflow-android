package com.rebuildit.prestaflow.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.notifications.NotificationCategoriesRepository
import com.rebuildit.prestaflow.domain.notifications.NotificationCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationCategoriesUiState(
    val categories: Map<NotificationCategory, Boolean> =
        NotificationCategory.entries.associateWith { true },
) {
    val allDisabled: Boolean get() = categories.values.none { it }
}

@HiltViewModel
class NotificationCategoriesViewModel
    @Inject
    constructor(
        private val categoriesRepository: NotificationCategoriesRepository,
    ) : ViewModel() {
        val uiState: StateFlow<NotificationCategoriesUiState> =
            categoriesRepository.categoryPreferences
                .map { prefs -> NotificationCategoriesUiState(categories = prefs) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = NotificationCategoriesUiState(),
                )

        fun setCategory(
            category: NotificationCategory,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                categoriesRepository.setCategory(category, enabled)
            }
        }
    }
