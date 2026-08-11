package com.rebuildit.prestaflow.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.auth.AuthRepository
import com.rebuildit.prestaflow.domain.auth.AuthState
import com.rebuildit.prestaflow.domain.capabilities.CapabilitiesRepository
import com.rebuildit.prestaflow.domain.sav.SavRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    ) : ViewModel() {
        val authState: StateFlow<AuthState> = authRepository.authState

        /**
         * Fils SAV non lus, pour la pastille de l'onglet Clients (chrome du shell, cf.
         * [com.rebuildit.prestaflow.ui.MainActivity]) — compensation à la descente de niveau du
         * SAV dans la nav, cf. étude `rebuild-it/docs/app-avis-sav.md`.
         *
         * [SharingStarted.Eagerly] (pas `WhileSubscribed`) : la pastille du shell doit refléter le
         * compte dès l'affichage, sans attendre qu'un premier collecteur Compose s'abonne.
         */
        val unreadSavCount: StateFlow<Int> =
            savRepository.unreadThreadCount.stateIn(
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
                    capabilitiesRepository.refresh()
                }
            }
        }

        fun logout() {
            viewModelScope.launch {
                authRepository.logout()
            }
        }
    }
