package com.rebuildit.prestaflow.ui.reviews

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Lot socle : aucune logique métier câblée (cf. [ReviewsUiState]). Ce ViewModel sera branché sur
 * un `ReviewsRepository` (file de modération, publication, rejet motivé — L111-7-2) dans le lot
 * Avis, une fois le contrat connecteur figé (le pont vers `rbreviews` côté module).
 */
@HiltViewModel
class ReviewsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow<ReviewsUiState>(ReviewsUiState.Empty)
        val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()
    }
