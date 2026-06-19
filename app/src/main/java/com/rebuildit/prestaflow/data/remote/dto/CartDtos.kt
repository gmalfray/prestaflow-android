package com.rebuildit.prestaflow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Réponse liste paniers — GET /baskets
 * Enveloppe {"data": [...]}
 */
@Serializable
data class CartListResponseDto(
    @SerialName("data") val data: List<CartListItemDto> = emptyList()
)

/**
 * Réponse détail panier — GET /baskets?id_cart=X
 * Enveloppe {"data": {...}}
 */
@Serializable
data class CartDetailResponseDto(
    @SerialName("data") val data: CartDetailDto? = null
)

/**
 * Item de liste de panier (schéma retourné par BasketsService::formatBasketRow).
 * Champs potentiellement absents → valeurs par défaut.
 */
@Serializable
data class CartListItemDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("customer") val customer: CartCustomerDto? = null,
    @SerialName("currency") val currency: CartCurrencyDto? = null,
    @SerialName("totals") val totals: CartTotalsDto? = null,
    @SerialName("items_count") val itemsCount: Int = 0,
    @SerialName("has_order") val hasOrder: Boolean = false,
    @SerialName("dates") val dates: CartDatesDto? = null
)

/**
 * Détail de panier : même structure que CartListItemDto, plus le champ products.
 */
@Serializable
data class CartDetailDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("customer") val customer: CartCustomerDto? = null,
    @SerialName("currency") val currency: CartCurrencyDto? = null,
    @SerialName("totals") val totals: CartTotalsDto? = null,
    @SerialName("items_count") val itemsCount: Int = 0,
    @SerialName("has_order") val hasOrder: Boolean = false,
    @SerialName("dates") val dates: CartDatesDto? = null,
    @SerialName("products") val products: List<CartProductDto> = emptyList()
)

@Serializable
data class CartCustomerDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("firstname") val firstname: String? = null,
    @SerialName("lastname") val lastname: String? = null,
    @SerialName("email") val email: String? = null
)

@Serializable
data class CartCurrencyDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("iso") val iso: String? = null
)

@Serializable
data class CartTotalsDto(
    @SerialName("tax_excl") val taxExcl: Double? = null,
    @SerialName("tax_incl") val taxIncl: Double? = null
)

@Serializable
data class CartDatesDto(
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CartProductDto(
    @SerialName("product_id") val productId: Int = 0,
    @SerialName("product_attribute_id") val productAttributeId: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("reference") val reference: String? = null,
    @SerialName("quantity") val quantity: Int = 0,
    @SerialName("total_tax_incl") val totalTaxIncl: Double = 0.0,
    @SerialName("total_tax_excl") val totalTaxExcl: Double = 0.0,
    @SerialName("image") val image: String? = null
)
