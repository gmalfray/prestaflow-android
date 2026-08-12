package com.rebuildit.prestaflow.domain.sav

import com.rebuildit.prestaflow.domain.sav.model.SavReplyResult
import com.rebuildit.prestaflow.domain.sav.model.SavThreadDetail
import com.rebuildit.prestaflow.domain.sav.model.SavThreadStatus
import com.rebuildit.prestaflow.domain.sav.model.SavThreadsPage
import kotlinx.coroutines.flow.Flow

/**
 * Port du SAV (fils clients, natif PrestaShop — capacité toujours vraie, cf.
 * [com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities.sav]). Hors périmètre
 * (volontaire, cf. étude `rebuild-it/docs/app-avis-sav.md`) : créer un fil, gérer les contacts,
 * les pièces jointes.
 */
interface SavRepository {
    /**
     * Nombre de fils SAV « à traiter » (depuis le connecteur v1.20.0), pour la pastille de
     * l'onglet Clients. **PAS** un compte de fils « non lus » — ce drapeau natif PrestaShop
     * (`unread`, cf. [com.rebuildit.prestaflow.domain.sav.model.SavThread.unread]) s'est révélé
     * inexploitable comme signal d'action sur une boutique dont le SAV est traité par e-mail (449
     * fils « non lus » mesurés en prod, dont 364 déjà clos et 190 déjà répondus — PrestaShop ne
     * pose ce drapeau qu'à l'ouverture d'un fil en BO natif). « À traiter » = fil non clos,
     * dernier message émis par la cliente, activité de moins de 90 jours — calculé exactement en
     * SQL côté connecteur, cf. Javadoc de l'implémentation.
     */
    val toProcessCount: Flow<Int>

    /**
     * Rafraîchit [toProcessCount] depuis le connecteur (`GET /sav/stats`, v1.20.0+) — compteur
     * exact, PAS une approximation par scan de page. Best-effort : un échec réseau conserve la
     * dernière valeur connue plutôt que de la remettre à zéro.
     */
    suspend fun refreshToProcessCount()

    /**
     * Charge une page de fils depuis `GET /sav`.
     *
     * @param status Filtre sur un statut natif précis. `null` = tous statuts, non-clos d'abord
     * (comportement par défaut du connecteur — c'est ce qui donne « les fils ouverts d'abord »).
     * @param limit Nombre de fils par page (défaut serveur 20, max serveur 100).
     * @param offset Décalage pour la pagination.
     */
    suspend fun fetchThreads(
        status: SavThreadStatus? = null,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
    ): SavThreadsPage

    /** Fil complet (métadonnées + messages chronologiques) depuis `GET /sav/{id}`. */
    suspend fun fetchThread(threadId: Long): SavThreadDetail

    /**
     * Change le statut d'un fil (`PATCH /sav/{id}/status`). Aucun message ajouté, aucun e-mail
     * envoyé — uniquement une mise à jour de statut.
     */
    suspend fun updateThreadStatus(
        threadId: Long,
        status: SavThreadStatus,
    )

    /**
     * ⚠️ Envoie un VRAI e-mail à la cliente, à l'adresse enregistrée sur le fil. Aucun brouillon,
     * aucune confirmation supplémentaire côté connecteur : c'est CET appel qui constitue l'acte
     * d'envoi. L'UI DOIT obtenir une confirmation explicite de l'utilisatrice avant d'appeler
     * cette méthode — jamais d'appel implicite (cf. `SavThreadDetailViewModel`/`SavThreadDetailScreen`).
     */
    suspend fun replyToThread(
        threadId: Long,
        message: String,
    ): SavReplyResult

    companion object {
        const val PAGE_SIZE = 20
    }
}
