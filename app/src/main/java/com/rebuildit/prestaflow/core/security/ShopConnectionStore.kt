package com.rebuildit.prestaflow.core.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
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
            // Absent des connexions persistées avant l'introduction du filtrage par scope côté app
            // (`emptyList()` par défaut, jamais une valeur réelle — le connecteur assigne toujours
            // des scopes non vides à un jeton authentique) : AuthRepositoryImpl.refreshScopesIfMissing()
            // détecte ce cas au démarrage et force un renouvellement silencieux du jeton plutôt que
            // de laisser une utilisatrice ayant pourtant le droit voir le SAV disparaître sans
            // explication (capacité != droit, cf. ClientsSection.visibleSections).
            val scopes: List<String> = emptyList(),
            val apiKey: String = "",
            // Absent des connexions persistées avant cette version : la valeur par défaut
            // (sav seul) s'applique tant qu'un refresh de CapabilitiesRepository n'a pas eu lieu.
            val capabilities: ShopCapabilities = ShopCapabilities(),
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

        /**
         * Met à jour les capacités d'UNE boutique persistée (no-op si [shopId] est inconnu —
         * ex. boutique supprimée entre-temps). Utilisé par
         * [com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository] après un
         * rafraîchissement réussi.
         */
        fun updateCapabilities(
            shopId: String,
            capabilities: ShopCapabilities,
        ) {
            val current = read()
            if (current.none { it.id == shopId }) return
            write(current.map { if (it.id == shopId) it.copy(capabilities = capabilities) else it })
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
                capabilities = capabilities,
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
                capabilities = capabilities,
            )

        private companion object {
            const val KEY_CONNECTIONS = "shop_connections"
            const val KEY_ACTIVE_ID = "shop_connections_active_id"
        }
    }
