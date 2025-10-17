package com.rebuildit.prestaflow.data.products.mapper

import com.rebuildit.prestaflow.data.local.entity.ProductEntity
import com.rebuildit.prestaflow.data.local.entity.StockAvailabilityEntity
import com.rebuildit.prestaflow.data.remote.dto.ProductDto
import com.rebuildit.prestaflow.data.remote.dto.StockDto
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.StockAvailability

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    sku = sku,
    priceTaxIncl = priceTaxIncl,
    active = active,
    stockQuantity = stockQuantity,
    imageUrl = imageUrl,
    lastUpdatedIso = lastUpdatedIso
)

fun StockAvailabilityEntity.toDomain(): StockAvailability = StockAvailability(
    productId = productId,
    warehouseId = warehouseId,
    quantity = quantity,
    updatedAtIso = updatedAtIso
)

fun ProductDto.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    sku = reference,
    priceTaxIncl = price,
    active = active,
    stockQuantity = stock.quantity,
    imageUrl = images.firstOrNull()?.url,
    lastUpdatedIso = updatedAt
)

fun StockDto.toEntity(productId: Long): StockAvailabilityEntity = StockAvailabilityEntity(
    productId = productId,
    warehouseId = warehouseId,
    quantity = quantity,
    updatedAtIso = updatedAt
)
