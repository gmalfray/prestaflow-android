package com.rebuildit.prestaflow.domain.products

import com.rebuildit.prestaflow.domain.products.model.ReplenishLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Journal PERSISTANT de la session de réappro en cours (cf. [ReplenishLogEntry]) — survit à la
 * fermeture de l'écran ou au processus tué en arrière-plan (backing Room, cf.
 * `com.rebuildit.prestaflow.data.products.ReplenishSessionRepositoryImpl`), contrairement à un
 * simple état de ViewModel. Rien n'est écrit côté serveur tant que la session n'est pas validée
 * définitivement ([com.rebuildit.prestaflow.ui.products.StockReplenishViewModel.onSubmitSession]) :
 * ce journal ne fait QUE lister/fusionner/retirer des lignes, jamais d'appel réseau.
 */
interface ReplenishSessionRepository {
    /** Journal courant, réactif — affiché tel quel par l'écran de réappro. */
    fun observeEntries(): Flow<List<ReplenishLogEntry>>

    /** Lecture ponctuelle du journal courant (cf. validation définitive : snapshot exact au moment d'envoyer). */
    suspend fun getEntries(): List<ReplenishLogEntry>

    /**
     * Ajoute [delta] au journal pour la cible ([productId]/[combinationId]/[warehouseId]) : fusionne
     * avec la ligne existante visant la MÊME cible si elle existe (delta additionné, la ligne garde
     * son id), sinon crée une nouvelle ligne. C'est cette fusion qui règle le défaut d'origine :
     * rescanner le même produit pendant que la session n'est pas encore validée s'additionne au lieu
     * de créer deux lignes indépendantes.
     */
    suspend fun addOrMerge(
        productId: Long,
        combinationId: Long?,
        warehouseId: Long?,
        productName: String,
        delta: Int,
    )

    /**
     * Retire la ligne [id] du journal — aucun appel réseau (rien n'a encore été écrit), donc ne peut
     * pas échouer. Utilisé à la fois pour une annulation manuelle et pour retirer une ligne dont
     * l'écriture a réussi lors de la validation définitive.
     */
    suspend fun removeEntry(id: Long)

    /** Vide entièrement le journal (fin de session, après validation intégrale sans échec). */
    suspend fun clear()
}
