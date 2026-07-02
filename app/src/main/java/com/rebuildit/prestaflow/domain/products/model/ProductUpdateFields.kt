package com.rebuildit.prestaflow.domain.products.model

/**
 * Champs simples de la fiche produit à modifier via [com.rebuildit.prestaflow.domain.products.ProductsRepository.updateProductFields].
 * Tous les champs sont nullables : seuls ceux renseignés (non-null) sont envoyés au serveur
 * (mise à jour partielle) — un champ non modifié par l'utilisateur reste `null` et n'est pas transmis.
 */
data class ProductUpdateFields(
    val name: String? = null,
    val description: String? = null,
    val descriptionShort: String? = null,
    val reference: String? = null,
    val priceTaxExcl: Double? = null,
    val active: Boolean? = null,
) {
    /** Aucun champ à envoyer : requête inutile (rien n'a changé). */
    val isEmpty: Boolean
        get() =
            name == null && description == null && descriptionShort == null &&
                reference == null && priceTaxExcl == null && active == null
}
