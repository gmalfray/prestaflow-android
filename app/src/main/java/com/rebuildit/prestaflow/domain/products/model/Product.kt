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
    /**
     * Combinaison (déclinaison) ayant matché le code-barres scanné (`GET products?barcode=`).
     * Non nul uniquement pour un résultat transitoire de scan sur un produit à déclinaisons
     * (ex. pelotes de laine : 1 coloris = 1 EAN13 + 1 stock propres) — absent sinon, y compris
     * pour le produit tel que mis en cache localement (Room ne modélise pas le stock par
     * combinaison, cf. [ProductsRepository.updateStock]).
     */
    val matchedCombination: MatchedCombination? = null,
    /**
     * Toutes les déclinaisons du produit (vide si le produit n'en a pas). Utilisé quand un scan
     * ne peut pas désigner une déclinaison précise ([matchedCombination] absent) mais que le
     * produit en porte plusieurs : l'UI doit alors laisser choisir laquelle ajuster/associer
     * plutôt que de deviner. Résultat transitoire de scan/recherche uniquement — non mis en
     * cache local (comme [matchedCombination]).
     */
    val combinations: List<Combination> = emptyList(),
) {
    /**
     * Quantité à afficher/éditer pour un résultat de scan : celle de la combinaison matchée si
     * le code scanné correspond à une déclinaison ([matchedCombination]), sinon celle du produit.
     */
    val scannedQuantity: Int get() = matchedCombination?.quantity ?: stock.quantity
}

/**
 * Combinaison (déclinaison) d'un produit ayant matché un scan code-barres, avec son propre stock
 * distinct de celui du produit parent.
 */
@Serializable
data class MatchedCombination(
    val id: Long,
    val name: String,
    val ean13: String? = null,
    val reference: String? = null,
    val quantity: Int,
)

/**
 * Déclinaison d'un produit parmi [Product.combinations] — même forme que [MatchedCombination]
 * mais couvre TOUTES les déclinaisons (pas seulement celle matchée par un scan), pour permettre
 * de proposer un choix à l'utilisateur.
 */
@Serializable
data class Combination(
    val id: Long,
    val name: String,
    val ean13: String? = null,
    val reference: String? = null,
    val quantity: Int,
)

/**
 * Convertit une déclinaison choisie parmi [Product.combinations] en [MatchedCombination], pour
 * réutiliser le même chemin d'affichage/soumission que celui d'un scan ayant matché directement
 * une combinaison.
 */
fun Combination.toMatchedCombination(): MatchedCombination =
    MatchedCombination(id = id, name = name, ean13 = ean13, reference = reference, quantity = quantity)

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
