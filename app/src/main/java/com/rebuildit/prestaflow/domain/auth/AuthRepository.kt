package com.rebuildit.prestaflow.domain.auth

import com.rebuildit.prestaflow.domain.auth.model.AuthToken
import com.rebuildit.prestaflow.domain.auth.model.ShopConnection
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Boutiques connectées (celle dont [ShopConnection.isActive] est vrai est l'active). */
    val connections: StateFlow<List<ShopConnection>>

    suspend fun login(
        shopUrl: String,
        apiKey: String,
    ): AuthResult

    /** Ajoute une boutique et la rend active (même flux que [login], avec un libellé). */
    suspend fun addConnection(
        shopUrl: String,
        apiKey: String,
        label: String,
    ): AuthResult

    /** Bascule la boutique active. */
    suspend fun switchActiveConnection(id: String)

    /**
     * Supprime une boutique connectée. Si c'était l'active, bascule sur une autre
     * (ou déconnecte s'il n'en reste aucune).
     */
    suspend fun removeConnection(id: String)

    suspend fun logout()

    suspend fun getActiveToken(): AuthToken?

    /**
     * Re-login silencieux de la boutique active avec sa clé API conservée, pour rafraîchir
     * un jeton expiré (appelé par l'Authenticator OkHttp sur un 401). Retourne true si un
     * nouveau jeton a été obtenu.
     */
    suspend fun refreshActiveToken(): Boolean
}
