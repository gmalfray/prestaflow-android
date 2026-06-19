package com.rebuildit.prestaflow.domain.carts.model

/**
 * Modèle domaine pour un panier en liste.
 */
data class CartSummary(
    val id: Int,
    val customerName: String,
    val customerEmail: String?,
    val currencyIso: String,
    val totalTaxIncl: Double,
    val itemsCount: Int,
    val hasOrder: Boolean,
    val createdAtIso: String?,
    val updatedAtIso: String?
)

/**
 * Modèle domaine pour le détail d'un panier, avec ses produits.
 */
data class CartDetail(
    val id: Int,
    val customerName: String,
    val customerEmail: String?,
    val currencyIso: String,
    val totalTaxIncl: Double,
    val totalTaxExcl: Double,
    val itemsCount: Int,
    val hasOrder: Boolean,
    val createdAtIso: String?,
    val updatedAtIso: String?,
    val products: List<CartProduct>
)

/**
 * Produit dans un panier.
 */
data class CartProduct(
    val productId: Int,
    val name: String,
    val reference: String?,
    val quantity: Int,
    val totalTaxIncl: Double,
    val imageUrl: String?
)
