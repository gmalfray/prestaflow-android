package com.rebuildit.prestaflow.data.carts.mapper

import com.rebuildit.prestaflow.data.remote.dto.CartDetailDto
import com.rebuildit.prestaflow.data.remote.dto.CartListItemDto
import com.rebuildit.prestaflow.data.remote.dto.CartProductDto
import com.rebuildit.prestaflow.domain.carts.model.CartDetail
import com.rebuildit.prestaflow.domain.carts.model.CartProduct
import com.rebuildit.prestaflow.domain.carts.model.CartSummary

fun CartListItemDto.toDomain(): CartSummary {
    val firstName = customer?.firstname.orEmpty()
    val lastName = customer?.lastname.orEmpty()
    val fullName = "$firstName $lastName".trim()
    return CartSummary(
        id = id,
        customerName = fullName.ifBlank { customer?.email.orEmpty() },
        customerEmail = customer?.email,
        currencyIso = currency?.iso.orEmpty(),
        totalTaxIncl = totals?.taxIncl ?: 0.0,
        itemsCount = itemsCount,
        hasOrder = hasOrder,
        createdAtIso = dates?.createdAt,
        updatedAtIso = dates?.updatedAt
    )
}

fun CartDetailDto.toDomain(): CartDetail {
    val firstName = customer?.firstname.orEmpty()
    val lastName = customer?.lastname.orEmpty()
    val fullName = "$firstName $lastName".trim()
    return CartDetail(
        id = id,
        customerName = fullName.ifBlank { customer?.email.orEmpty() },
        customerEmail = customer?.email,
        currencyIso = currency?.iso.orEmpty(),
        totalTaxIncl = totals?.taxIncl ?: 0.0,
        totalTaxExcl = totals?.taxExcl ?: 0.0,
        itemsCount = itemsCount,
        hasOrder = hasOrder,
        createdAtIso = dates?.createdAt,
        updatedAtIso = dates?.updatedAt,
        products = products.map { it.toDomain() }
    )
}

fun CartProductDto.toDomain(): CartProduct = CartProduct(
    productId = productId,
    name = name.orEmpty(),
    reference = reference,
    quantity = quantity,
    totalTaxIncl = totalTaxIncl,
    imageUrl = image
)
