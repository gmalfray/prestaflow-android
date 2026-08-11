package com.rebuildit.prestaflow.domain.reviews

/**
 * Règle de validation du motif de rejet d'un avis — article L111-7-2 du code de la consommation :
 * l'auteur d'un avis non publié doit être informé du motif, et le module envoie cet e-mail à la
 * mise en corbeille. Alignée sur la validation serveur (`rebuild-connector`,
 * `POST reviews/{id}/trash` → `422 invalid_rejection_reason` en dessous de ce seuil, validé
 * "AVANT toute écriture en base" côté connecteur).
 *
 * Centralisée ici plutôt que dupliquée dans chaque écran/geste : c'est la SEULE porte d'entrée
 * pour savoir si un motif est recevable, afin qu'aucun geste de rejet — bouton direct dans la
 * liste, swipe futur, etc. — ne puisse contourner la règle (cf. étude
 * `rebuild-it/docs/app-avis-sav.md` § « pas de rejet rapide sans motif »).
 */
object ReviewRejectionReason {
    const val MIN_LENGTH = 10

    fun isValid(reason: String): Boolean = reason.trim().length >= MIN_LENGTH
}
