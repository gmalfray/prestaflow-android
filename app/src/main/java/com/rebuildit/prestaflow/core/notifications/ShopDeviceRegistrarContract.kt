package com.rebuildit.prestaflow.core.notifications

/**
 * Contrat d'enregistrement/désenregistrement FCM auprès d'une boutique précise.
 *
 * Extrait de [ShopDeviceRegistrar] pour permettre l'injection de fakes en test.
 */
interface ShopDeviceRegistrarContract {
    /**
     * Enregistre (ou met à jour) le device FCM [fcmToken] auprès de la boutique
     * identifiée par [shopUrl], en utilisant le JWT [shopToken].
     */
    suspend fun registerOnShop(
        shopUrl: String,
        shopToken: String,
        fcmToken: String,
        topics: List<String> = emptyList(),
    )

    /**
     * Désenregistre le device FCM [fcmToken] de la boutique [shopUrl].
     */
    suspend fun unregisterFromShop(
        shopUrl: String,
        shopToken: String,
        fcmToken: String,
    )
}
