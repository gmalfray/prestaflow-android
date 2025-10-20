package com.rebuildit.prestaflow.core.network

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rebuildit.prestaflow.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
class ApiEndpointManager @Inject constructor(
    private val sharedPreferences: SharedPreferences
) {

    private val defaultBaseUrl: HttpUrl = BuildConfig.API_BASE_URL.toHttpUrl()

    @Volatile
    private var activeBaseUrl: HttpUrl =
        sharedPreferences.getString(KEY_BASE_URL, null)?.toHttpUrlOrNull() ?: defaultBaseUrl

    @Volatile
    private var activeShopUrl: String? = sharedPreferences.getString(KEY_SHOP_URL, null)

    fun getDefaultBaseUrl(): HttpUrl = defaultBaseUrl

    fun getActiveBaseUrl(): HttpUrl = activeBaseUrl

    fun hasOverride(): Boolean = activeBaseUrl != defaultBaseUrl

    fun buildApiBaseUrl(shopUrl: String): HttpUrl? {
        val sanitized = shopUrl.trim().trimEnd('/')
        if (sanitized.isEmpty()) {
            return null
        }
        val candidate = "$sanitized/module/rebuildconnector/api/"
        return candidate.toHttpUrlOrNull()
    }

    fun setActiveBaseUrl(baseUrl: HttpUrl, shopUrl: String, persist: Boolean) {
        activeBaseUrl = baseUrl
        if (persist) {
            activeShopUrl = shopUrl
            sharedPreferences.edit {
                putString(KEY_BASE_URL, baseUrl.toString())
                putString(KEY_SHOP_URL, shopUrl)
            }
        }
    }

    fun clearOverride() {
        activeBaseUrl = defaultBaseUrl
        activeShopUrl = null
        sharedPreferences.edit {
            remove(KEY_BASE_URL)
            remove(KEY_SHOP_URL)
        }
    }

    fun getStoredShopUrl(): String? = activeShopUrl

    fun extractRelativePath(originalUrl: HttpUrl): String {
        val originalPath = originalUrl.encodedPath
        val basePath = defaultBaseUrl.encodedPath

        return if (originalPath.startsWith(basePath)) {
            originalPath.substring(basePath.length)
        } else {
            originalPath.trimStart('/')
        }
    }

    companion object {
        private const val KEY_BASE_URL = "api_base_url_override"
        private const val KEY_SHOP_URL = "shop_url_override"
    }
}
