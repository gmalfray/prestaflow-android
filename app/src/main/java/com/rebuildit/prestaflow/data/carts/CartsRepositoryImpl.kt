package com.rebuildit.prestaflow.data.carts

import com.rebuildit.prestaflow.data.carts.mapper.toDomain
import com.rebuildit.prestaflow.data.remote.api.PrestaFlowApi
import com.rebuildit.prestaflow.domain.carts.CartsRepository
import com.rebuildit.prestaflow.domain.carts.model.CartDetail
import com.rebuildit.prestaflow.domain.carts.model.CartSummary
import javax.inject.Inject

class CartsRepositoryImpl
    @Inject
    constructor(
        private val api: PrestaFlowApi,
    ) : CartsRepository {
        override suspend fun getCarts(abandonedSinceDays: Int): List<CartSummary> {
            val response =
                api.getBaskets(
                    abandonedSinceDays = if (abandonedSinceDays > 0) abandonedSinceDays else null,
                )
            return response.data.map { it.toDomain() }
        }

        override suspend fun getCartById(cartId: Int): CartDetail? {
            val response = api.getBasketById(cartId)
            return response.data?.toDomain()
        }
    }
