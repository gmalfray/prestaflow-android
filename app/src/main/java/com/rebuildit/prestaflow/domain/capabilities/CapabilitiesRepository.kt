package com.rebuildit.prestaflow.domain.capabilities

import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import kotlinx.coroutines.flow.StateFlow

/**
 * Capacités de la boutique **active** — cf. [ShopCapabilities] pour la distinction capacité/droit.
 *
 * [capabilities] reflète la dernière valeur connue (persistée par boutique, cf.
 * [com.rebuildit.prestaflow.core.security.ShopConnectionStore]) : avant tout [refresh] réussi
 * sur cette installation, la valeur par défaut ne porte que `sav` (les autres capacités restent
 * masquées plutôt que supposées présentes — cf. étude § « l'app masque l'onglet, elle ne se
 * contente pas de griser »).
 */
interface CapabilitiesRepository {
    val capabilities: StateFlow<ShopCapabilities>

    /**
     * Interroge le connecteur de la boutique active et met à jour [capabilities] (persisté pour
     * cette boutique). À appeler après login/changement de boutique active, et périodiquement
     * (une boutique peut désinstaller un module sans que l'app le sache).
     *
     * En cas d'échec réseau, [capabilities] n'est pas modifié (dernière valeur connue conservée).
     */
    suspend fun refresh(): ShopCapabilities
}
