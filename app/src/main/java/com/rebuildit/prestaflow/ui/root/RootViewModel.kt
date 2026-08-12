package com.rebuildit.prestaflow.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.reviews.ReviewsRepository
import com.rebuildit.prestaflow.domain.sav.SavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val capabilitiesRepository: CapabilitiesRepository,
        savRepository: SavRepository,
        reviewsRepository: ReviewsRepository,
    ) : ViewModel() {
        val authState: StateFlow<AuthState> = authRepository.authState

        /**
         * Pastille de l'onglet Clients (chrome du shell, cf. [com.rebuildit.prestaflow.ui.MainActivity]) :
         * somme des fils SAV non lus et des avis en attente de modération — la part avis n'est
         * comptée que si la boutique active porte la capacité `reviews` (module `rbreviews`
         * installé), sinon un avis resterait invisible depuis l'extérieur de l'onglet alors que le
         * connecteur ne l'expose même pas. Compensation à la descente de niveau du SAV (et des
         * Avis) dans la nav, cf. étude `rebuild-it/docs/app-avis-sav.md`.
         *
         * [SharingStarted.Eagerly] (pas `WhileSubscribed`) : la pastille du shell doit refléter le
         * compte dès l'affichage, sans attendre qu'un premier collecteur Compose s'abonne.
         */
        val clientsBadgeCount: StateFlow<Int> =
            combine(
                savRepository.unreadThreadCount,
                reviewsRepository.pendingReviewCount,
                capabilitiesRepository.capabilities,
            ) { unreadSav, pendingReviews, capabilities ->
                unreadSav + if (capabilities.reviews) pendingReviews else 0
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = 0,
            )

        init {
            // Capacités vérifiées "à chaud" à chaque entrée en session authentifiée : après un
            // login, ET après un changement de boutique active (le token change, donc cette même
            // branche Authenticated se redéclenche avec une nouvelle valeur) — cf. étude § « une
            // boutique peut désinstaller le module sans que l'app le sache ».
            viewModelScope.launch {
                authState.filterIsInstance<AuthState.Authenticated>().collect {
                    val capabilities = capabilitiesRepository.refresh()
                    savRepository.refreshUnreadCount()
                    // Jamais d'appel `GET /reviews` sur une boutique sans le module rbreviews : le
                    // connecteur répondrait 409 (cf. Javadoc de refreshPendingCount).
                    if (capabilities.reviews) {
                        reviewsRepository.refreshPendingCount()
                    }
                }
            }
        }

        fun logout() {
            viewModelScope.launch {
                authRepository.logout()
            }
        }
    }
