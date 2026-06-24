package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.auth.model.ShopConnection

/**
 * Implémentation en mémoire de la logique de [com.rebuildit.prestaflow.core.security.ShopConnectionStore]
 * pour les tests d'[com.rebuildit.prestaflow.data.auth.AuthRepositoryImpl].
 *
 * Expose un accès direct à l'état interne pour les assertions de test.
 */
class FakeShopConnectionStore {
    private val connections = mutableListOf<ShopConnection>()
    private var activeId: String? = null

    fun read(): List<ShopConnection> = connections.toList()

    fun write(newConnections: List<ShopConnection>) {
        connections.clear()
        connections.addAll(newConnections)
    }

    fun getActiveId(): String? = activeId

    fun setActiveId(id: String?) {
        activeId = id
    }

    fun clear() {
        connections.clear()
        activeId = null
    }
}
