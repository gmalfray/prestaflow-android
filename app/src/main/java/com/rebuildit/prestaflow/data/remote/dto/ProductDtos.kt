package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductListResponseDto(
    @SerialName("products") val products: List<ProductDto>,
    @SerialName("pagination") val pagination: PaginationDto? = null,
)

@Serializable
data class ProductDetailResponseDto(
    @SerialName("product") val product: ProductDto,
)

@Serializable
data class ProductDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("reference") val reference: String? = null,
    @SerialName("price") val price: Double,
    @SerialName("active") val active: Boolean,
    @SerialName("stock") val stock: StockDto,
    @SerialName("images") val images: List<ImageDto> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class StockDto(
    @SerialName("quantity") val quantity: Int,
    @SerialName("warehouse_id") val warehouseId: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_low") val isLow: Boolean = false,
    @SerialName("low_stock_threshold") val lowStockThreshold: Int = 0,
)

@Serializable
data class ImageDto(
    @SerialName("id") val id: Long,
    @SerialName("url") val url: String,
)

@Serializable
data class StockUpdateRequestDto(
    @SerialName("quantity") val quantity: Int,
    @SerialName("warehouse_id") val warehouseId: Long? = null,
    @SerialName("reason") val reason: String? = null,
)
