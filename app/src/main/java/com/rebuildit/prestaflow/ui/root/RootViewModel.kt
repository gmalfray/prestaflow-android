package com.rebuildit.prestaflow.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.auth.model.AuthScopes
import com.rebuildit.prestaflow.domain.auth.scopes
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
         * somme des fils SAV non lus et des avis en attente de modération. Chaque part n'est
         * comptée que si les DEUX conditions de [com.rebuildit.prestaflow.ui.clients.ClientsSection.visibleSections]
         * sont réunies — capacité de la boutique ET scope du jeton (`sav.read` / `reviews.moderate`)
         * — capacité ≠ droit, cf. étude `rebuild-it/docs/app-avis-sav.md` § « Capacité ≠ droit » et
         * le défaut vécu par Greg (pastille SAV visible sans le scope, 403 à l'ouverture). Sinon la
         * pastille du shell annoncerait un total dont une partie serait invisible ou inaccessible
         * depuis l'extérieur de l'onglet.
         *
         * [SharingStarted.Eagerly] (pas `WhileSubscribed`) : la pastille du shell doit refléter le
         * compte dès l'affichage, sans attendre qu'un premier collecteur Compose s'abonne.
         */
        val clientsBadgeCount: StateFlow<Int> =
            combine(
                savRepository.unreadThreadCount,
                reviewsRepository.pendingReviewCount,
                capabilitiesRepository.capabilities,
                authState,
            ) { unreadSav, pendingReviews, capabilities, auth ->
                val scopes = auth.scopes
                val savCount = if (AuthScopes.SAV_READ in scopes) unreadSav else 0
                val reviewsCount = if (capabilities.reviews && AuthScopes.REVIEWS_MODERATE in scopes) pendingReviews else 0
                savCount + reviewsCount
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
                authState.filterIsInstance<AuthState.Authenticated>().collect { authenticated ->
                    val capabilities = capabilitiesRepository.refresh()
                    val scopes = authenticated.token.scopes.toSet()
                    // Jamais d'appel `GET /sav` sans le scope sav.read : le connecteur répondrait
                    // 403 (cf. défaut remonté par Greg — capacité toujours vraie ≠ droit du jeton).
                    if (AuthScopes.SAV_READ in scopes) {
                        savRepository.refreshUnreadCount()
                    }
                    // Jamais d'appel `GET /reviews` sur une boutique sans le module rbreviews (409)
                    // NI sans le scope reviews.moderate (403) — cf. Javadoc de refreshPendingCount.
                    if (capabilities.reviews && AuthScopes.REVIEWS_MODERATE in scopes) {
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
