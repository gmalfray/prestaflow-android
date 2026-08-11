package com.rebuildit.prestaflow.domain.auth.model

import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities

/**
 * Une boutique PrestaShop connectée dans l'app (multi-boutiques).
 *
 * @param id Identifiant stable de la connexion (l'URL normalisée de la boutique).
 * @param shopUrl URL HTTPS normalisée de la boutique.
 * @param label Libellé affiché (saisi par l'utilisateur ou dérivé de l'URL).
 * @param token Jeton d'authentification associé à cette boutique.
 * @param apiKey Clé API de la boutique, conservée (chiffrée) pour le re-login automatique
 *   quand le jeton expire (TTL court côté module).
 * @param isActive Vrai pour la boutique actuellement sélectionnée (celle vers laquelle
 *   les requêtes sont routées).
 * @param capabilities Dernières capacités connues de cette boutique (persistées pour éviter un
 *   flash de sous-navigation au démarrage, rafraîchies à chaud par
 *   [com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository]). Distinct de
 *   [token]/scopes — cf. [ShopCapabilities].
 */
data class ShopConnection(
    val id: String,
    val shopUrl: String,
    val label: String,
    val token: AuthToken,
    val apiKey: String = "",
    val isActive: Boolean = false,
    val capabilities: ShopCapabilities = ShopCapabilities(),
)
