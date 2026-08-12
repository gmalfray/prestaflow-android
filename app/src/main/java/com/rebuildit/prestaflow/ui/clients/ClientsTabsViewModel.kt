package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.ViewModel
import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.domain.reviews.ReviewsRepository
import com.rebuildit.prestaflow.domain.sav.SavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Alimente la sous-navigation Clients/SAV/Avis (cf. [ClientsSection]) avec les capacités de la
 * boutique active, ainsi que les compteurs affichés sur les sous-onglets SAV et Avis (répartition
 * du chiffre agrégé de la pastille du shell, cf.
 * [com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount]). Le rafraîchissement réseau
 * lui-même est déclenché en amont (login / changement de boutique, cf.
 * [com.rebuildit.prestaflow.ui.root.RootViewModel]) — ce ViewModel ne fait qu'observer les mêmes
 * flux de repository (source de vérité unique), sans redéclencher de rafraîchissement.
 */
@HiltViewModel
class ClientsTabsViewModel
    @Inject
    constructor(
        capabilitiesRepository: CapabilitiesRepository,
        savRepository: SavRepository,
        reviewsRepository: ReviewsRepository,
    ) : ViewModel() {
        val capabilities: StateFlow<ShopCapabilities> = capabilitiesRepository.capabilities
        val unreadSavCount: Flow<Int> = savRepository.unreadThreadCount
        val pendingReviewCount: Flow<Int> = reviewsRepository.pendingReviewCount
    }
