package com.rebuildit.prestaflow.data.capabilities

import com.rebuildit.prestaflow.core.security.ShopConnectionStore
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.data.remote.dto.toDomain
import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capacités de la boutique active — cf. [CapabilitiesRepository].
 *
 * Le connecteur ne met rien en cache côté serveur (« vérifié à chaud », cf. étude
 * `rebuild-it/docs/app-avis-sav.md`) : c'est ici, côté app, que la dernière valeur connue est
 * conservée (par boutique, dans [ShopConnectionStore]) pour éviter un flash de sous-navigation à
 * chaque démarrage, en attendant qu'un [refresh] confirme l'état réel.
 */
@Singleton
class CapabilitiesRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
        private val connectionStore: ShopConnectionStore,
        private val ioDispatcher: CoroutineDispatcher,
    ) : CapabilitiesRepository {
        private val _capabilities = MutableStateFlow(persistedCapabilitiesFor(connectionStore.getActiveId()))
        override val capabilities: StateFlow<ShopCapabilities> = _capabilities

        override suspend fun refresh(): ShopCapabilities =
            withContext(ioDispatcher) {
                val activeId = connectionStore.getActiveId()
                // Bascule immédiate sur la dernière valeur connue de la boutique ACTIVE avant même
                // l'appel réseau : utile après un changement de boutique (switchActiveConnection),
                // pour ne jamais exposer un instant les capacités de l'ancienne boutique le temps
                // que la requête réseau aboutisse.
                _capabilities.value = persistedCapabilitiesFor(activeId)

                val fetched =
                    runCatching { api.getCapabilities().toDomain() }
                        .onFailure { error ->
                            // Échec réseau : on GARDE la dernière valeur connue plutôt que de la
                            // réinitialiser — un onglet Avis ne doit pas disparaître à cause d'un
                            // simple accroc réseau (contrairement à une vraie désinstallation,
                            // reflétée au prochain appel réussi).
                            Timber.w(error, "Échec du rafraîchissement des capacités boutique")
                        }.getOrNull()
                        ?: return@withContext _capabilities.value

                _capabilities.value = fetched
                if (activeId != null) connectionStore.updateCapabilities(activeId, fetched)
                fetched
            }

        private fun persistedCapabilitiesFor(shopId: String?): ShopCapabilities {
            shopId ?: return ShopCapabilities()
            return connectionStore.read().firstOrNull { it.id == shopId }?.capabilities ?: ShopCapabilities()
        }
    }
