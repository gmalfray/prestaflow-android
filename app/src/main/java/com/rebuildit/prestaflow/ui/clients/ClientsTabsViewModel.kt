package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.ViewModel
import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Alimente la sous-navigation Clients/SAV/Avis (cf. [ClientsSection]) avec les capacités de la
 * boutique active. Le rafraîchissement réseau lui-même est déclenché en amont (login / changement
 * de boutique, cf. [com.rebuildit.prestaflow.ui.root.RootViewModel]) — ce ViewModel ne fait
 * qu'observer la valeur courante.
 */
@HiltViewModel
class ClientsTabsViewModel
    @Inject
    constructor(
        capabilitiesRepository: CapabilitiesRepository,
    ) : ViewModel() {
        val capabilities: StateFlow<ShopCapabilities> = capabilitiesRepository.capabilities
    }
