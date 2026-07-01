package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.carts.model.CartDetail
import com.rebuildit.prestaflow.domain.carts.model.CartSummary

/**
 * Fake en mémoire de [CartsRepository] pour les tests JVM.
 *
 * - [cartsResult] : liste retournée par [getCarts].
 * - [shouldThrow] : force un échec réseau si vrai.
 */
class FakeCartsRepository : CartsRepository {
    var cartsResult: List<CartSummary> = emptyList()
    var shouldThrow: Boolean = false
    var cartDetailResult: CartDetail? = null

    override suspend fun getCarts(abandonedSinceDays: Int): List<CartSummary> {
        if (shouldThrow) throw RuntimeException("Erreur réseau simulée")
        return cartsResult
    }

    override suspend fun getCartById(cartId: Int): CartDetail? {
        if (shouldThrow) throw RuntimeException("Erreur réseau simulée")
        return cartDetailResult
    }
}
