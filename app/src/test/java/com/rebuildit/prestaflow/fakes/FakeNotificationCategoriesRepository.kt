package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.notifications.NotificationCategoriesRepository
import com.rebuildit.prestaflow.domain.notifications.NotificationCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake de [NotificationCategoriesRepository] pour les tests unitaires.
 * Chaque catégorie démarre à son [NotificationCategory.defaultEnabled] (comme l'implémentation
 * réelle) : toutes actives sauf [NotificationCategory.REVIEW_PENDING].
 */
class FakeNotificationCategoriesRepository : NotificationCategoriesRepository {
    private val prefsState =
        MutableStateFlow(
            NotificationCategory.entries.associateWith { it.defaultEnabled },
        )
    override val categoryPreferences: Flow<Map<NotificationCategory, Boolean>> = prefsState

    override suspend fun setCategory(
        category: NotificationCategory,
        enabled: Boolean,
    ) {
        prefsState.value = prefsState.value + (category to enabled)
    }

    private val notifyFreeState = MutableStateFlow(true)
    override val notifyFreeOrders: Flow<Boolean> = notifyFreeState

    override suspend fun setNotifyFreeOrders(enabled: Boolean) {
        notifyFreeState.value = enabled
    }

    override suspend fun isNotifyFreeOrders(): Boolean = notifyFreeState.value

    override suspend fun enabledTopics(): List<String> =
        prefsState.value
            .filter { (_, enabled) -> enabled }
            .keys
            .map { it.key }
}
