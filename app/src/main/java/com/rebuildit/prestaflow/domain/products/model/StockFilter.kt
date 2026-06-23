package com.rebuildit.prestaflow.domain.products.model

/** Filtre d'état de stock pour l'écran Produits. */
enum class StockFilter(val apiValue: String?) {
    ALL(null),
    IN_STOCK("in_stock"),
    OUT_OF_STOCK("out_of_stock"),
    LOW_STOCK("low_stock"),
}
