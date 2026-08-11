package com.rebuildit.prestaflow.domain.capabilities.model

import kotlinx.serialization.Serializable

/**
 * Capacités de la boutique active : ce dont le module connecteur est **capable**, indépendamment
 * des droits de l'utilisatrice (scopes du JWT, cf. [com.rebuildit.prestaflow.domain.auth.model.AuthToken]).
 *
 * Distinction volontaire — cf. étude `rebuild-it/docs/app-avis-sav.md` § « Capacité ≠ droit » :
 * un JWT peut très bien porter un scope `reviews.moderate` sur une boutique où le module d'avis
 * n'est pas installé. La capacité, elle, reflète l'état réel du connecteur, vérifié à chaud.
 *
 * `@Serializable` : ce modèle domaine est persisté tel quel par
 * [com.rebuildit.prestaflow.core.security.ShopConnectionStore] (JSON en `SharedPreferences`),
 * au même titre que le reste d'une [com.rebuildit.prestaflow.domain.auth.model.ShopConnection].
 *
 * @param sav Fils clients (SAV) — toujours vrai : natif PrestaShop, aucun module requis.
 * @param reviews Modération d'avis — nécessite le module `rbreviews` installé ET actif.
 * @param shippingLabels Génération d'étiquettes transporteur (déjà implémentée côté app ;
 *   exposée ici pour compatibilité avec le contrat, non encore utilisée pour du masquage UI).
 */
@Serializable
data class ShopCapabilities(
    val sav: Boolean = true,
    val reviews: Boolean = false,
    val shippingLabels: Boolean = false,
)
