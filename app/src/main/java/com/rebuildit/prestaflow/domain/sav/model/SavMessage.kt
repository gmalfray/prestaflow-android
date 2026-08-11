package com.rebuildit.prestaflow.domain.sav.model

/** Auteur d'un message SAV — `employee` si `id_employee > 0` côté connecteur, `customer` sinon. */
enum class SavMessageAuthor {
    CUSTOMER,
    EMPLOYEE,
}

/** Message d'un fil SAV, dans l'ordre chronologique croissant (cf. `GET /sav/{id}`). */
data class SavMessage(
    val id: Long,
    val author: SavMessageAuthor,
    /** Nom de l'employé auteur (uniquement si [author] == [SavMessageAuthor.EMPLOYEE] nommé). */
    val employeeName: String?,
    val message: String,
    val private: Boolean,
    val read: Boolean,
    val dateAddedIso: String?,
)

/** Fil complet : métadonnées + tous les messages (`GET /sav/{id}`). */
data class SavThreadDetail(
    val thread: SavThread,
    val messages: List<SavMessage>,
)

/**
 * Résultat d'une réponse envoyée (`POST /sav/{id}/reply`) — ⚠️ cet appel envoie un VRAI e-mail à
 * la cliente, cf. [com.rebuildit.prestaflow.domain.sav.SavRepository.replyToThread].
 *
 * [emailSent] peut être `false` sans que l'appel ait échoué : le connecteur ignore
 * silencieusement l'envoi si le fil n'a pas d'adresse e-mail exploitable (cf. doc connecteur).
 * L'UI doit refléter cette nuance plutôt que de toujours annoncer un succès d'envoi.
 */
data class SavReplyResult(
    val thread: SavThread,
    val message: SavMessage,
    val emailSent: Boolean,
)
