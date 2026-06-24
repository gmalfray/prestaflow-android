package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.core.notifications.ShopDeviceRegistrarContract

/**
 * Fake de [ShopDeviceRegistrarContract] pour les tests unitaires.
 *
 * Enregistre les appels sans faire aucune requête réseau réelle.
 */
class FakeShopDeviceRegistrar : ShopDeviceRegistrarContract {

    data class RegisterCall(val shopUrl: String, val fcmToken: String, val topics: List<String>)
    data class UnregisterCall(val shopUrl: String, val fcmToken: String)

    val registerCalls: MutableList<RegisterCall> = mutableListOf()
    val unregisterCalls: MutableList<UnregisterCall> = mutableListOf()

    override suspend fun registerOnShop(
        shopUrl: String,
        shopToken: String,
        fcmToken: String,
        topics: List<String>,
    ) {
        registerCalls += RegisterCall(shopUrl, fcmToken, topics)
    }

    override suspend fun unregisterFromShop(
        shopUrl: String,
        shopToken: String,
        fcmToken: String,
    ) {
        unregisterCalls += UnregisterCall(shopUrl, fcmToken)
    }
}
