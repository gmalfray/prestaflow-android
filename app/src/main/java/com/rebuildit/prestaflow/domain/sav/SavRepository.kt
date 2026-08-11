package com.rebuildit.prestaflow.domain.sav

import kotlinx.coroutines.flow.Flow

/**
 * Port du SAV (fils clients, natif PrestaShop — capacité toujours vraie, cf.
 * [com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities.sav]).
 *
 * Lot socle (capacités + sous-navigation « Clients ») : seul [unreadThreadCount] est câblé, pour
 * alimenter la pastille de l'onglet Clients — la compensation assumée à la descente de niveau du
 * SAV dans la nav (cf. étude `rebuild-it/docs/app-avis-sav.md` § « Contrepartie assumée »).
 *
 * La liste des fils, le détail et la réponse arrivent avec le lot SAV proprement dit, une fois le
 * contrat du connecteur figé (cf. étude § « Ordre de travail proposé »).
 */
interface SavRepository {
    /** Nombre de fils SAV non lus, pour la pastille de l'onglet Clients. */
    val unreadThreadCount: Flow<Int>
}
