package com.rebuildit.prestaflow.domain.products.model

/**
 * Filtre de la liste Produits.
 *
 * Chaque valeur porte les paramètres de requête à transmettre au connecteur :
 * - [stockParam] : paramètre `stock` (`in_stock`/`out_of_stock`/`low_stock`). Le connecteur
 *   restreint déjà ce filtre aux produits **actifs** — ne jamais l'associer à [activeParam].
 * - [activeParam] : paramètre `active` (`"0"`/`"1"`). Utilisé uniquement par [INACTIVE] pour ne
 *   récupérer que les produits désactivés, sans filtre de stock.
 *
 * [ALL] ne transmet ni l'un ni l'autre : le connecteur renvoie alors actifs + inactifs.
 */
enum class StockFilter(val stockParam: String?, val activeParam: String? = null) {
    ALL(stockParam = null),
    IN_STOCK(stockParam = "in_stock"),
    OUT_OF_STOCK(stockParam = "out_of_stock"),
    LOW_STOCK(stockParam = "low_stock"),
    INACTIVE(stockParam = null, activeParam = "0"),
}
