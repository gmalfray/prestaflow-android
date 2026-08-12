package com.rebuildit.prestaflow.domain.sav.model

/**
 * Statuts natifs `ps_customer_thread.status` (ENUM PrestaShop, inchangée de 1.6 à 8.x — cf.
 * rebuild-connector `docs/api.md` § SAV). Noms explicites en Kotlin plutôt que les libellés
 * cryptiques `pending1`/`pending2` du schéma natif, pour que le code d'app reste lisible sans
 * avoir la doc connecteur sous les yeux.
 */
enum class SavThreadStatus(
    /** Valeur exacte attendue par le connecteur (corps JSON, paramètre `status`). */
    val apiValue: String,
) {
    /** Fil jamais traité. */
    OPEN("open"),

    /** En attente d'une réponse de la CLIENTE — le marchand vient de répondre (`pending1`). */
    AWAITING_CUSTOMER_REPLY("pending1"),

    /** En attente d'une réponse du MARCHAND — la cliente vient d'écrire/relancer (`pending2`). */
    AWAITING_MERCHANT_REPLY("pending2"),

    /** Fil clos. */
    CLOSED("closed"),
    ;

    companion object {
        /** Retombe sur [OPEN] si le connecteur renvoie une valeur inconnue (rétrocompatibilité). */
        fun fromApiValue(value: String): SavThreadStatus = entries.firstOrNull { it.apiValue == value } ?: OPEN
    }
}

/**
 * Fil SAV — ligne de liste ou en-tête de détail (`GET /sav` et `GET /sav/{id}`, même forme pour
 * les métadonnées). `id_shop` est déjà filtré côté connecteur (protection IDOR multiboutique) :
 * un fil d'une autre boutique n'apparaît jamais ici.
 */
data class SavThread(
    val id: Long,
    val status: SavThreadStatus,
    /**
     * ⚠️ Au moins un message de la cliente marqué non lu (convention du connecteur) — mais
     * quasiment toujours vrai sur une boutique dont le SAV est traité par e-mail (PrestaShop ne
     * pose ce drapeau qu'à l'ouverture en BO natif). Usage PAR FIL uniquement (ex. détail) ;
     * **jamais** pour un compteur global, cf. [toProcess].
     */
    val unread: Boolean,
    /**
     * Définition « à traiter » exacte (depuis le connecteur v1.20.0) : fil non clos, dernier
     * message de la cliente, activité de moins de 90 jours. C'est ce champ qui doit conditionner
     * tout badge/mise en avant par fil — pas [unread].
     */
    val toProcess: Boolean,
    val customerId: Long?,
    val customerName: String?,
    val customerEmail: String?,
    val orderId: Long?,
    val orderReference: String?,
    val lastMessageAtIso: String?,
    val dateAddedIso: String?,
    val dateUpdatedIso: String?,
)

/** Page paginée de fils SAV issue de `GET /sav`. */
data class SavThreadsPage(
    val threads: List<SavThread>,
    val hasNext: Boolean,
    val nextOffset: Int,
)
