package com.rebuildit.prestaflow.domain.notifications

import kotlinx.coroutines.flow.Flow

/**
 * Préférences de catégories de notifications, par appareil.
 *
 * Chaque catégorie est stockée comme un booléen indépendant dans DataStore.
 * Défaut : toutes les catégories activées.
 */
interface NotificationCategoriesRepository {
    /** Flux de la map catégorie → activée/désactivée. */
    val categoryPreferences: Flow<Map<NotificationCategory, Boolean>>

    /** Active ou désactive une catégorie. */
    suspend fun setCategory(
        category: NotificationCategory,
        enabled: Boolean,
    )

    /** Renvoie la liste des clés des catégories actuellement activées. */
    suspend fun enabledTopics(): List<String>
}
