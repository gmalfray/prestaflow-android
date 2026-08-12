package com.rebuildit.prestaflow.domain.auth.model

/**
 * Noms exacts des scopes JWT vérifiés côté connecteur (source de vérité : `rebuild-connector`
 * `controllers/front/SavController.php`/`ReviewsController.php` § `requireAuth`).
 *
 * Capacité ≠ droit (cf. [com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities]) :
 * une capacité vraie dit seulement que la boutique EST CAPABLE d'une fonctionnalité, pas que le
 * jeton de l'utilisatrice y donne accès. Un onglet dont la capacité est vraie mais dont le scope
 * manque doit rester masqué EXACTEMENT comme un onglet dont la capacité est fausse — sinon
 * l'utilisatrice l'ouvre et se heurte à un `403` (cf. défaut remonté par Greg sur le SAV).
 */
object AuthScopes {
    /** Lecture des fils SAV — `GET /sav`, `GET /sav/{id}`. */
    const val SAV_READ = "sav.read"

    /** Modération des avis — toutes les routes `/reviews` (publish/trash/reply compris). */
    const val REVIEWS_MODERATE = "reviews.moderate"
}
