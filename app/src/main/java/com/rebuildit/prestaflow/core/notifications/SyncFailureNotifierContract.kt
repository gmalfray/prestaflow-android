package com.rebuildit.prestaflow.core.notifications

import com.rebuildit.prestaflow.domain.sync.model.PendingSyncTask

/**
 * Rend visible l'abandon définitif d'une tâche de synchronisation offline (ex. réponse HTTP
 * 400/422 : requête rejetée par le serveur, aucun intérêt à rejouer).
 *
 * Avant ce contrat, [com.rebuildit.prestaflow.core.sync.SyncTaskExecutor] se contentait d'un
 * `Timber.w` puis retirait silencieusement la tâche de la file : l'utilisateur ne savait jamais
 * qu'une modification faite hors-ligne n'avait jamais été appliquée côté boutique.
 *
 * Extrait en interface (cf. [ShopDeviceRegistrarContract]) pour permettre l'injection de fakes
 * en test JVM pur (pas de dépendance Android/Context dans [SyncTaskExecutor]).
 */
interface SyncFailureNotifierContract {
    /** Signale que [task] a été abandonnée après une réponse HTTP [httpCode] non réessayable. */
    fun notifyTaskDropped(
        task: PendingSyncTask,
        httpCode: Int,
    )
}
