package com.rebuildit.prestaflow.ui.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.scopes
import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.capabilities.model.ShopCapabilities
import com.rebuildit.prestaflow.domain.reviews.ReviewsRepository
import com.rebuildit.prestaflow.domain.sav.SavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Alimente la sous-navigation Clients/SAV/Avis (cf. [ClientsSection]) avec les capacités de la
 * boutique active, les scopes du jeton actif (les DEUX déterminent, ensemble, quelles sections
 * [ClientsSection.visibleSections] rend visibles — capacité ≠ droit), ainsi que les compteurs
 * affichés sur les sous-onglets SAV et Avis (répartition du chiffre agrégé de la pastille du
 * shell, cf. [com.rebuildit.prestaflow.ui.root.RootViewModel.clientsBadgeCount]). Le
 * rafraîchissement réseau lui-même est déclenché en amont (login / changement de boutique, cf.
 * [com.rebuildit.prestaflow.ui.root.RootViewModel]) — ce ViewModel ne fait qu'observer les mêmes
 * flux de repository (source de vérité unique), sans redéclencher de rafraîchissement.
 */
@HiltViewModel
class ClientsTabsViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        capabilitiesRepository: CapabilitiesRepository,
        savRepository: SavRepository,
        reviewsRepository: ReviewsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val capabilities: StateFlow<ShopCapabilities> = capabilitiesRepository.capabilities
        val savToProcessCount: Flow<Int> = savRepository.toProcessCount
        val pendingReviewCount: Flow<Int> = reviewsRepository.pendingReviewCount

        /**
         * Sous-onglet à afficher à l'ouverture, porté par l'argument de navigation `section` (cf.
         * le deep link `prestaflow://clients?section=reviews` envoyé par un tap sur une
         * notification "avis à modérer" —
         * [com.rebuildit.prestaflow.data.push.PrestaFlowFirebaseMessagingService.buildReviewsDeepLinkIntent]).
         * Absent ou toute autre valeur → [ClientsSection.CLIENTS], le comportement historique d'un
         * accès direct via la barre du bas. Si la section demandée n'est finalement pas visible
         * (capacité/scope manquant), [ClientsTabsScreen] retombe elle-même sur Clients.
         */
        val initialSection: ClientsSection =
            when (savedStateHandle.get<String>("section")) {
                "reviews" -> ClientsSection.REVIEWS
                else -> ClientsSection.CLIENTS
            }

        // Valeur initiale calculée de façon SYNCHRONE (StateFlow.value, pas de suspension) : sans
        // ça, la première composition verrait un instant un onglet SAV/Avis (ou son absence)
        // incohérent avec le jeton réellement actif, le temps qu'une première valeur soit collectée.
        val scopes: StateFlow<Set<String>> =
            authRepository.authState
                .map { it.scopes }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = authRepository.authState.value.scopes,
                )
    }
