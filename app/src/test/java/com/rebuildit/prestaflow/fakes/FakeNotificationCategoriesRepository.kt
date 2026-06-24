package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.notifications.NotificationCategoriesRepository
import com.rebuildit.prestaflow.domain.notifications.NotificationCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake de [NotificationCategoriesRepository] pour les tests unitaires.
 * Toutes les catégories sont actives par défaut.
 */
class FakeNotificationCategoriesRepository : NotificationCategoriesRepository {
    private val _prefs =
        MutableStateFlow(
            NotificationCategory.entries.associateWith { true },
        )
    override val categoryPreferences: Flow<Map<NotificationCategory, Boolean>> = _prefs

    override suspend fun setCategory(
        category: NotificationCategory,
        enabled: Boolean,
    ) {
        _prefs.value = _prefs.value + (category to enabled)
    }

    override suspend fun enabledTopics(): List<String> =
        _prefs.value
            .filter { (_, enabled) -> enabled }
            .keys
            .map { it.key }
}
