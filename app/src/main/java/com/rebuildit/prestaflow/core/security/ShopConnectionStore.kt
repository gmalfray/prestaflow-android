package com.rebuildit.prestaflow.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistance chiffrée des connexions multi-boutiques (réutilise le SharedPreferences
 * chiffré injecté, comme [EncryptedTokenStorage]). Stocke la liste en JSON + l'id de
 * la boutique active.
 */
@Singleton
class ShopConnectionStore
    @Inject
    constructor(
        private val sharedPreferences: SharedPreferences,
    ) {
        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class Stored(
            val id: String,
            val shopUrl: String,
            val label: String,
            val token: String,
            val expiresAt: Long? = null,
            val scopes: List<String> = emptyList(),
            val apiKey: String = "",
        )

        fun read(): List<ShopConnection> {
            val raw = sharedPreferences.getString(KEY_CONNECTIONS, null) ?: return emptyList()
            return runCatching {
                json.decodeFromString<List<Stored>>(raw).map { it.toDomain() }
            }.onFailure { Timber.w(it, "Failed to read shop connections") }
                .getOrDefault(emptyList())
        }

        fun write(connections: List<ShopConnection>) {
            val stored = connections.map { it.toStored() }
            sharedPreferences.edit {
                putString(KEY_CONNECTIONS, json.encodeToString(stored))
            }
        }

        fun getActiveId(): String? = sharedPreferences.getString(KEY_ACTIVE_ID, null)

        fun setActiveId(id: String?) {
            sharedPreferences.edit {
                if (id == null) remove(KEY_ACTIVE_ID) else putString(KEY_ACTIVE_ID, id)
            }
        }

        fun clear() {
            sharedPreferences.edit {
                remove(KEY_CONNECTIONS)
                remove(KEY_ACTIVE_ID)
            }
        }

        private fun Stored.toDomain() =
            ShopConnection(
                id = id,
                shopUrl = shopUrl,
                label = label,
                token = AuthToken(value = token, expiresAtEpochMillis = expiresAt, scopes = scopes),
                apiKey = apiKey,
            )

        private fun ShopConnection.toStored() =
            Stored(
                id = id,
                shopUrl = shopUrl,
                label = label,
                token = token.value,
                expiresAt = token.expiresAtEpochMillis,
                scopes = token.scopes,
                apiKey = apiKey,
            )

        private companion object {
            const val KEY_CONNECTIONS = "shop_connections"
            const val KEY_ACTIVE_ID = "shop_connections_active_id"
        }
    }
