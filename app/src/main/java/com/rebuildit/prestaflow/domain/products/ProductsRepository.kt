package com.rebuildit.prestaflow.domain.products

import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductUpdateFields
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.coroutines.flow.Flow

interface ProductsRepository {
    fun observeProducts(): Flow<List<Product>>

    fun observeProduct(productId: Long): Flow<Product?>

    fun observeStockAvailabilities(productId: Long): Flow<List<StockAvailability>>

    /**
     * Rafraîchit la liste des produits depuis le serveur.
     * @param stockFilter Si non nul, filtre par état de stock : "in_stock", "out_of_stock" ou "low_stock".
     * @param search Si non nul, délègue la recherche textuelle à l'API (nom, référence).
     * @return Le total réel de produits rapporté par l'API (selon filtres actifs), ou null si la requête échoue.
     */
    suspend fun refresh(
        forceRemote: Boolean = false,
        stockFilter: String? = null,
        search: String? = null,
    ): Int?

    suspend fun refreshProduct(
        productId: Long,
        forceRemote: Boolean = false,
    )

    /**
     * Recherche des produits par code-barres exact (EAN13/EAN8 ou référence), via l'API
     * uniquement (pas de cache local — résultat transitoire pour le flux de scan).
     * @return La liste des produits correspondants (généralement 0 ou 1, rarement plusieurs).
     */
    suspend fun searchByBarcode(barcode: String): List<Product>

    /**
     * Recherche textuelle de produits (nom/référence), via l'API uniquement (résultat
     * transitoire, pas de cache local) — utilisée pour choisir le produit auquel associer un
     * code-barres scanné qui n'a matché aucun produit.
     */
    suspend fun searchProducts(query: String): List<Product>

    /**
     * Associe le code-barres [ean13] au produit [productId] (cas d'un scan sans correspondance :
     * le produit existe mais son EAN13 n'est pas encore renseigné côté boutique).
     * @return Le produit tel que renvoyé par le serveur après mise à jour.
     */
    suspend fun setProductEan13(
        productId: Long,
        ean13: String,
    ): Product

    /**
     * Met à jour les champs simples de la fiche produit (nom, description, description courte,
     * référence, prix HT, actif) via `PATCH products/{id}` — mise à jour PARTIELLE : seuls les
     * champs non-null de [fields] sont envoyés.
     * @return Le produit tel que renvoyé par le serveur après mise à jour.
     */
    suspend fun updateProductFields(
        productId: Long,
        fields: ProductUpdateFields,
    ): Product

    suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long? = null,
        reason: String? = null,
    )

    suspend fun updatePrice(
        productId: Long,
        price: Double,
    )

    suspend fun updateStatus(
        productId: Long,
        active: Boolean,
    )
}
