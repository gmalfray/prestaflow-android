package com.rebuildit.prestaflow.domain.products.model

/**
 * Ligne du journal de session de réappro (cf. [com.rebuildit.prestaflow.domain.products.ReplenishSessionRepository]) :
 * un ajustement de stock scanné mais PAS ENCORE écrit côté serveur — rien ne part tant que la
 * session n'est pas validée définitivement ([com.rebuildit.prestaflow.ui.products.StockReplenishViewModel.onSubmitSession]).
 *
 * Une seule ligne par cible (même [productId]/[combinationId]/[warehouseId]) : rescanner le même
 * produit accumule dans [delta] au lieu de créer une seconde ligne (cf.
 * [com.rebuildit.prestaflow.domain.products.ReplenishSessionRepository.addOrMerge]) — c'est ce qui
 * règle le défaut d'origine (un scan sur deux perdu en rescannant le même produit).
 *
 * [delta] est un incrément SIGNÉ, jamais une quantité absolue : la validation définitive écrit
 * chaque ligne via [com.rebuildit.prestaflow.domain.products.ProductsRepository.adjustStock] (mode
 * incrémental côté connecteur) plutôt que d'écraser le stock avec une valeur absolue — une session
 * dure plusieurs minutes, pendant lesquelles la boutique peut vendre le produit.
 */
data class ReplenishLogEntry(
    val id: Long,
    val productId: Long,
    val combinationId: Long?,
    val warehouseId: Long?,
    val productName: String,
    val delta: Int,
)

/** Récap dérivé du journal (nb de lignes + somme des unités) — plus d'état séparé à maintenir en sync. */
data class ReplenishSessionRecap(
    val articleCount: Int = 0,
    val unitsCount: Int = 0,
) {
    companion object {
        fun from(entries: List<ReplenishLogEntry>) =
            ReplenishSessionRecap(articleCount = entries.size, unitsCount = entries.sumOf { it.delta })
    }
}
