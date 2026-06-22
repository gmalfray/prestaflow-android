package com.rebuildit.prestaflow.domain.auth.model

/**
 * Une boutique PrestaShop connectée dans l'app (multi-boutiques).
 *
 * @param id Identifiant stable de la connexion (l'URL normalisée de la boutique).
 * @param shopUrl URL HTTPS normalisée de la boutique.
 * @param label Libellé affiché (saisi par l'utilisateur ou dérivé de l'URL).
 * @param token Jeton d'authentification associé à cette boutique.
 * @param isActive Vrai pour la boutique actuellement sélectionnée (celle vers laquelle
 *   les requêtes sont routées).
 */
data class ShopConnection(
    val id: String,
    val shopUrl: String,
    val label: String,
    val token: AuthToken,
    val isActive: Boolean = false,
)
