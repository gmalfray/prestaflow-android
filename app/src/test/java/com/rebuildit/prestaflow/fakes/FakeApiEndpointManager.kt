package com.rebuildit.prestaflow.fakes

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Fake en mémoire imitant le comportement de [com.rebuildit.prestaflow.core.network.ApiEndpointManager].
 *
 * Implémente uniquement les méthodes utilisées par [com.rebuildit.prestaflow.data.auth.AuthRepositoryImpl].
 */
class FakeApiEndpointManager {
    private var activeBaseUrl: HttpUrl? = null
    private var storedShopUrl: String? = null
    var setActiveBaseUrlCalls: Int = 0
    var clearOverrideCalls: Int = 0

    fun buildApiBaseUrl(shopUrl: String): HttpUrl? {
        val sanitized = shopUrl.trim().trimEnd('/')
        if (sanitized.isEmpty()) return null
        return "$sanitized/module/rebuildconnector/api/".toHttpUrlOrNull()
    }

    fun setActiveBaseUrl(
        baseUrl: HttpUrl,
        shopUrl: String,
        persist: Boolean,
    ) {
        activeBaseUrl = baseUrl
        if (persist) storedShopUrl = shopUrl
        setActiveBaseUrlCalls++
    }

    fun clearOverride() {
        activeBaseUrl = null
        storedShopUrl = null
        clearOverrideCalls++
    }

    fun getStoredShopUrl(): String? = storedShopUrl
}
