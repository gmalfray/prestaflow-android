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
    /** Nombre de fils SAV non lus, pour la pastille de l'onglet Clients. */
    val unreadThreadCount: Flow<Int>

    /**
     * Rafraîchit [unreadThreadCount] depuis le connecteur. Le connecteur n'exposant aucun
     * compteur dédié, l'implémentation approxime à partir de la première page de fils non-clos —
     * voir la Javadoc de l'implémentation pour la limite exacte. Best-effort : un échec réseau
     * conserve la dernière valeur connue plutôt que de la remettre à zéro.
     */
    suspend fun refreshUnreadCount()

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
