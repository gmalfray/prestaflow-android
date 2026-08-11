package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Fake en mémoire de [CapabilitiesRepository]. */
class FakeCapabilitiesRepository(
    initial: ShopCapabilities = ShopCapabilities(),
) : CapabilitiesRepository {
    private val _capabilities = MutableStateFlow(initial)
    override val capabilities: StateFlow<ShopCapabilities> = _capabilities

    /** Valeur renvoyée par le prochain [refresh]. Null → conserve la valeur courante (échec simulé). */
    var nextRefreshResult: ShopCapabilities? = null

    /** Nombre d'appels reçus par [refresh]. */
    var refreshCallCount: Int = 0

    override suspend fun refresh(): ShopCapabilities {
        refreshCallCount++
        nextRefreshResult?.let { _capabilities.value = it }
        return _capabilities.value
    }

    /** Émet directement une nouvelle valeur, sans passer par [refresh] (simule un état déjà connu). */
    fun emit(capabilities: ShopCapabilities) {
        _capabilities.value = capabilities
    }
}
