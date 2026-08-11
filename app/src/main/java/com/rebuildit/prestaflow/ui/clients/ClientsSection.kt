package com.rebuildit.prestaflow.ui.clients

import androidx.annotation.StringRes
import com.rebuildit.prestaflow.R
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

    /** Natif PrestaShop — capacité toujours vraie, cf. [ShopCapabilities.sav] : jamais masqué. */
    SAV(R.string.clients_section_sav),

    /** Nécessite le module `rbreviews` — masqué (pas grisé) si [ShopCapabilities.reviews] est faux. */
    REVIEWS(R.string.clients_section_reviews),
    ;

    companion object {
        /**
         * Sections visibles compte tenu des capacités de la boutique active.
         *
         * C'est LE point à risque signalé par l'étude (§ « Capacité ≠ droit ») : [REVIEWS] ne doit
         * apparaître que si [ShopCapabilities.reviews] est vrai — une fonction absente du
         * connecteur ne doit laisser aucune trace dans l'UI, pas même une entrée désactivée.
         */
        fun visibleSections(capabilities: ShopCapabilities): List<ClientsSection> = entries.filter { it != REVIEWS || capabilities.reviews }
    }
}
