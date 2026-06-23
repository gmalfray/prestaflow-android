package com.rebuildit.prestaflow.core.notifications

import com.rebuildit.prestaflow.core.network.ApiEndpointManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enregistre/désenregistre le device FCM sur le backend d'une boutique **précise**
 * (≠ la boutique active). Utilise un client HTTP dédié (sans les intercepteurs
 * dynamiques URL/token de l'app, qui pointeraient sinon vers la boutique active).
 *
 * Sert au multi-boutiques : enregistrer le device sur TOUTES les boutiques connectées
 * (rotation du token FCM) et le désenregistrer d'une boutique supprimée.
 */
@Singleton
class ShopDeviceRegistrar
    @Inject
    constructor(
        private val endpointManager: ApiEndpointManager,
        private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val client = OkHttpClient()

        suspend fun registerOnShop(
            shopUrl: String,
            shopToken: String,
            fcmToken: String,
            topics: List<String> = emptyList(),
        ) {
            val base = endpointManager.buildApiBaseUrl(shopUrl) ?: return
            val url = base.newBuilder().addPathSegments("notifications/devices").build()
            val body =
                buildJsonObject {
                    put("token", JsonPrimitive(fcmToken))
                    put("platform", JsonPrimitive("android"))
                    put(
                        "topics",
                        kotlinx.serialization.json.buildJsonArray {
                            topics.forEach { add(JsonPrimitive(it)) }
                        },
                    )
                }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request =
                Request.Builder()
                    .url(url)
                    .post(body)
                    .header("Authorization", "Bearer $shopToken")
                    .build()
            execute(request, "register", shopUrl)
        }

        suspend fun unregisterFromShop(
            shopUrl: String,
            shopToken: String,
            fcmToken: String,
        ) {
            val base = endpointManager.buildApiBaseUrl(shopUrl) ?: return
            val encoded = URLEncoder.encode(fcmToken, Charsets.UTF_8.name())
            val url = base.newBuilder().addPathSegments("notifications/devices/$encoded").build()
            val request =
                Request.Builder()
                    .url(url)
                    .delete()
                    .header("Authorization", "Bearer $shopToken")
                    .build()
            execute(request, "unregister", shopUrl)
        }

        private suspend fun execute(
            request: Request,
            action: String,
            shopUrl: String,
        ) {
            withContext(ioDispatcher) {
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Timber.w("FCM %s sur %s : HTTP %d", action, shopUrl, response.code)
                        }
                    }
                }.onFailure { Timber.w(it, "FCM %s sur %s a échoué", action, shopUrl) }
            }
        }

        private companion object {
            val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }
    }
