package com.rebuildit.prestaflow.data.products.mapper

import com.rebuildit.prestaflow.data.local.entity.ProductEntity
import com.rebuildit.prestaflow.data.local.entity.StockAvailabilityEntity
import com.rebuildit.prestaflow.data.remote.dto.ProductDto
import com.rebuildit.prestaflow.data.remote.dto.StockDto
import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductImage
import com.rebuildit.prestaflow.domain.products.model.ProductStock
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun ProductEntity.toDomain(): Product {
    val stock = json.decodeFromString<ProductStock>(stockJson)
    val images = json.decodeFromString<List<ProductImage>>(imagesJson)

    return Product(
        id = id,
        name = name,
        reference = reference,
        price = price,
        active = active,
        stock = stock,
        images = images,
        updatedAt = updatedAt,
    )
}

fun StockAvailabilityEntity.toDomain(): StockAvailability =
    StockAvailability(
        productId = productId,
        warehouseId = warehouseId.takeUnless { it == StockAvailabilityEntity.NO_WAREHOUSE_ID },
        quantity = quantity,
        updatedAtIso = updatedAtIso,
    )

/**
 * Mappe un [ProductDto] directement en modèle domaine [Product], sans passer par Room.
 * Utilisé pour les résultats transitoires (ex. recherche par code-barres scanné) qu'on ne
 * souhaite pas mettre en cache local.
 */
fun ProductDto.toDomain(): Product =
    Product(
        id = id,
        name = name,
        reference = reference.orEmpty(),
        price = price,
        active = active,
        stock = stock.toDomainStock(),
        images = images.map { ProductImage(id = it.id, url = it.url) },
        updatedAt = updatedAt ?: java.time.Instant.now().toString(),
        ean13 = ean13,
    )

fun ProductDto.toEntity(): ProductEntity {
    val stockJson = json.encodeToString(stock.toDomainStock())
    val imagesJson = json.encodeToString(images)

    return ProductEntity(
        id = id,
        name = name,
        reference = reference.orEmpty(),
        price = price,
        active = active,
        stockJson = stockJson,
        imagesJson = imagesJson,
        updatedAt = updatedAt ?: java.time.Instant.now().toString(),
    )
}

fun StockDto.toEntity(productId: Long): StockAvailabilityEntity =
    StockAvailabilityEntity(
        productId = productId,
        warehouseId = warehouseId ?: StockAvailabilityEntity.NO_WAREHOUSE_ID,
        quantity = quantity,
        updatedAtIso = updatedAt,
    )

fun StockDto.toDomainStock(): ProductStock =
    ProductStock(
        quantity = quantity,
        warehouseId = warehouseId,
        updatedAt = updatedAt,
        isLow = isLow,
        lowStockThreshold = lowStockThreshold,
    )
