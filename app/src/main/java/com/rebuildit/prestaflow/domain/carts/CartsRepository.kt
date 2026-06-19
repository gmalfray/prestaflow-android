package com.rebuildit.prestaflow.domain.carts

import com.rebuildit.prestaflow.domain.carts.model.CartDetail
import com.rebuildit.prestaflow.domain.carts.model.CartSummary

interface CartsRepository {
    /**
     * Récupère la liste des paniers.
     * @param abandonedSinceDays si > 0, filtre les paniers abandonnés depuis ce nombre de jours.
     */
    suspend fun getCarts(abandonedSinceDays: Int = 0): List<CartSummary>

    /**
     * Récupère le détail d'un panier par son id.
     */
    suspend fun getCartById(cartId: Int): CartDetail?
}
