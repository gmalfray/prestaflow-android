package com.rebuildit.prestaflow.domain.notifications

import kotlinx.coroutines.flow.Flow

/**
 * Préférences de catégories de notifications, par appareil.
 *
 * Chaque catégorie est stockée comme un booléen indépendant dans DataStore.
 * Défaut tant que l'utilisateur n'a jamais touché à l'interrupteur : [NotificationCategory.defaultEnabled].
 */
interface NotificationCategoriesRepository {
    /** Flux de la map catégorie → activée/désactivée. */
    val categoryPreferences: Flow<Map<NotificationCategory, Boolean>>

    /** Active ou désactive une catégorie. */
    suspend fun setCategory(
        category: NotificationCategory,
        enabled: Boolean,
    )

    /**
     * Sous-option de [NotificationCategory.ORDER_CREATED] : afficher aussi les notifications des
     * commandes offertes (0 €). Booléen local (pas un topic FCM). Défaut : true.
     */
    val notifyFreeOrders: Flow<Boolean>

    suspend fun setNotifyFreeOrders(enabled: Boolean)

    /** Lecture ponctuelle de [notifyFreeOrders] (utilisée par le service FCM). */
    suspend fun isNotifyFreeOrders(): Boolean

    /** Renvoie la liste des clés des catégories actuellement activées. */
    suspend fun enabledTopics(): List<String>
}
