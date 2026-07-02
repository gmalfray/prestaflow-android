package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductListResponseDto(
    @SerialName("products") val products: List<ProductDto>,
    @SerialName("total") val total: Int = 0,
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
    // Rétrocompat : absent tant que le connecteur ne l'expose pas (défaut null).
    @SerialName("ean13") val ean13: String? = null,
    // Champs "fiche produit" (édition champs simples, cf. ProductUpdateRequestDto) : absents tant
    // que le connecteur ne les expose pas encore côté GET (défaut null → écran d'édition prérempli vide/
    // approximé le temps de l'alignement du contrat).
    @SerialName("description") val description: String? = null,
    @SerialName("description_short") val descriptionShort: String? = null,
    @SerialName("price_tax_excl") val priceTaxExcl: Double? = null,
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

/**
 * Corps du `PATCH /products/{id}` (action "attributes" côté connecteur, inférée par l'absence de
 * `quantity`). Requête PARTIELLE : tous les champs sont nullables et seuls ceux non-null sont
 * sérialisés (cf. `Json { explicitNulls = false }` fourni par Hilt) — un champ omis n'est donc pas
 * modifié côté serveur. Couvre l'édition des champs simples de la fiche produit + l'association
 * d'un code-barres scanné ([ean13]).
 */
@Serializable
data class ProductUpdateRequestDto(
    @SerialName("name") val name: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("description_short") val descriptionShort: String? = null,
    @SerialName("reference") val reference: String? = null,
    @SerialName("price_tax_excl") val priceTaxExcl: Double? = null,
    @SerialName("active") val active: Boolean? = null,
    @SerialName("ean13") val ean13: String? = null,
)
