package com.rebuildit.prestaflow.ui.clients

import androidx.annotation.StringRes
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.auth.model.AuthScopes
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities

/**
 * Sous-navigation interne de l'onglet Clients — PAS une entrée d'[com.rebuildit.prestaflow.navigation.AppDestination]
 * (qui reste un enum à 6 entrées, cf. étude `rebuild-it/docs/app-avis-sav.md` § « Navigation : tout
 * passe par Clients »). Le SAV et les Avis vivent ici, dans le même onglet que la liste clients,
 * pour rester rattachés au contexte d'une cliente (ses fils SAV, ses avis, à côté de ses commandes).
 */
enum class ClientsSection(
    @StringRes val labelRes: Int,
) {
    CLIENTS(R.string.clients_section_clients),

    /**
     * Natif PrestaShop — capacité toujours vraie (cf. [ShopCapabilities.sav]), mais masqué si le
     * jeton ne porte pas [AuthScopes.SAV_READ] : capacité ≠ droit, cf. [visibleSections].
     */
    SAV(R.string.clients_section_sav),

    /**
     * Nécessite le module `rbreviews` — masqué (pas grisé) si [ShopCapabilities.reviews] est faux
     * OU si le jeton ne porte pas [AuthScopes.REVIEWS_MODERATE].
     */
    REVIEWS(R.string.clients_section_reviews),
    ;

    companion object {
        /**
         * Sections visibles compte tenu des capacités de la boutique active ET des scopes portés
         * par le jeton actif.
         *
         * C'est LE point à risque signalé par l'étude (§ « Capacité ≠ droit ») — et le défaut vécu
         * par Greg : un jeton peut très bien ne PAS porter `sav.read` sur une boutique où le SAV
         * est nativement disponible (capacité toujours vraie). Filtrer sur la seule capacité
         * laissait alors le sous-onglet visible pour se heurter à un `403` en l'ouvrant. Les DEUX
         * conditions doivent être vraies pour qu'une section requérant un droit apparaisse.
         */
        fun visibleSections(
            capabilities: ShopCapabilities,
            scopes: Set<String>,
        ): List<ClientsSection> =
            entries.filter { section ->
                when (section) {
                    CLIENTS -> true
                    SAV -> AuthScopes.SAV_READ in scopes
                    REVIEWS -> capabilities.reviews && AuthScopes.REVIEWS_MODERATE in scopes
                }
            }
    }
}
