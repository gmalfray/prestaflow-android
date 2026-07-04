package com.rebuildit.prestaflow.domain.products

import com.rebuildit.prestaflow.domain.products.model.Product
import com.rebuildit.prestaflow.domain.products.model.ProductUpdateFields
import com.rebuildit.prestaflow.domain.products.model.StockAvailability
import kotlinx.coroutines.flow.Flow
import java.io.File

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

    /**
     * Compte, côté serveur, le nombre total de produits correspondant à un état de stock, sans
     * dépendre de la pagination locale ni du filtre actif de l'écran. Un seul appel API léger
     * (`limit=1`, on ne lit que le `total`). Sert à alimenter le KPI « Stock faible » avec un chiffre
     * **stable et identique au total du filtre** (le comptage sur la liste chargée était faux car
     * toutes les pages ne sont pas forcément en mémoire).
     * @param stockFilter "in_stock", "out_of_stock", "low_stock", ou null pour tout le catalogue.
     * @return Le total serveur, ou null si la requête échoue.
     */
    suspend fun countByStock(stockFilter: String?): Int?

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
     * le produit existe mais son EAN13 n'est pas encore renseigné côté boutique), ou à l'une de
     * ses combinaisons (déclinaisons) si [combinationId] est renseigné (cas d'un produit à
     * plusieurs déclinaisons — cf. [com.rebuildit.prestaflow.domain.products.model.Combination]).
     * @return Le produit tel que renvoyé par le serveur après mise à jour.
     */
    suspend fun setProductEan13(
        productId: Long,
        ean13: String,
        combinationId: Long? = null,
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

    /**
     * Met à jour le stock du produit [productId], ou celui d'une de ses combinaisons (déclinaisons)
     * si [combinationId] est renseigné (cas d'un scan ayant matché une combinaison — cf.
     * [com.rebuildit.prestaflow.domain.products.model.MatchedCombination]). Le connecteur applique
     * l'ajustement à la combinaison plutôt qu'au produit parent dans ce cas.
     */
    suspend fun updateStock(
        productId: Long,
        quantity: Int,
        warehouseId: Long? = null,
        reason: String? = null,
        combinationId: Long? = null,
    )

    /**
     * Ajoute [image] (fichier JPEG déjà préparé/compressé) à la fiche produit [productId] via
     * `POST products/{id}/images` (multipart, part `image`).
     * @return Le produit tel que renvoyé par le serveur, avec sa liste d'images à jour.
     */
    suspend fun uploadProductImage(
        productId: Long,
        image: File,
    ): Product

    /**
     * Supprime l'image [imageId] de la fiche produit [productId] via
     * `DELETE products/{id}/images/{imageId}`.
     * @return Le produit tel que renvoyé par le serveur, avec sa liste d'images à jour.
     */
    suspend fun deleteProductImage(
        productId: Long,
        imageId: Long,
    ): Product

    suspend fun updatePrice(
        productId: Long,
        price: Double,
    )

    suspend fun updateStatus(
        productId: Long,
        active: Boolean,
    )
}
