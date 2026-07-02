package com.rebuildit.prestaflow.domain.products.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Long,
    val name: String,
    val reference: String,
    val price: Double,
    val active: Boolean,
    val stock: ProductStock,
    val images: List<ProductImage>,
    val updatedAt: String,
    /**
     * Code-barres EAN13 (ou EAN8/référence) du produit, utilisé pour la recherche par scan.
     * Nullable : le connecteur peut ne pas encore exposer ce champ (rétrocompat versions antérieures).
     */
    val ean13: String? = null,
    /**
     * Champs "fiche produit" (édition champs simples). Nullable : le connecteur peut ne pas
     * encore les exposer côté GET (défaut null tant que le contrat n'est pas aligné).
     */
    val description: String? = null,
    val descriptionShort: String? = null,
    /**
     * Prix HT (`price_tax_excl` côté connecteur), distinct de [price] qui est le prix TTC affiché.
     * Nullable tant que le connecteur ne l'expose pas en lecture.
     */
    val priceTaxExcl: Double? = null,
)

@Serializable
data class ProductStock(
    val quantity: Int,
    val warehouseId: Long? = null,
    val updatedAt: String? = null,
    val isLow: Boolean = false,
    val lowStockThreshold: Int = 0,
)

@Serializable
data class ProductImage(
    val id: Long,
    val url: String,
)
