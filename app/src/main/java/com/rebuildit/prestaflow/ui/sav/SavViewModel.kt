package com.rebuildit.prestaflow.ui.sav

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Lot socle : aucune logique métier câblée (cf. [SavUiState] et étude
 * `rebuild-it/docs/app-avis-sav.md` § « Ordre de travail proposé »). L'écran démarre en état vide
 * plutôt qu'un chargement qui ne se terminerait jamais, faute de source de données réelle pour
 * l'instant. Ce ViewModel sera branché sur [com.rebuildit.prestaflow.domain.sav.SavRepository]
 * (liste des fils) dans le lot SAV, une fois le contrat connecteur figé.
 */
@HiltViewModel
class SavViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow<SavUiState>(SavUiState.Empty)
        val uiState: StateFlow<SavUiState> = _uiState.asStateFlow()
    }
