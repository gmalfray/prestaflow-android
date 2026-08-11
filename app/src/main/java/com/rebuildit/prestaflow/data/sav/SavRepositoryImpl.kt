package com.rebuildit.prestaflow.data.sav

import com.rebuildit.prestaflow.domain.sav.SavRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation SOCLE de [SavRepository] : le contrat de listing des fils SAV n'est pas encore
 * figé côté connecteur (cf. étude `rebuild-it/docs/app-avis-sav.md` § « Ordre de travail
 * proposé » — le SAV est le lot suivant, une fois cette base capacités/navigation posée).
 *
 * [unreadThreadCount] émet volontairement `0` (jamais un chiffre inventé) : la pastille de
 * l'onglet Clients reste donc invisible tant que ce repository n'est pas branché sur un vrai
 * endpoint. À remplacer par un appel réseau réel (`GET .../sav/threads?...` ou équivalent, à
 * valider avec `api-contract-guardian`) dans le lot SAV.
 */
@Singleton
class SavRepositoryImpl
    @Inject
    constructor() : SavRepository {
        override val unreadThreadCount: Flow<Int> = flowOf(0)
    }
