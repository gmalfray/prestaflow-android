package com.rebuildit.prestaflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.products.StockReplenishPreferencesRepository
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pilote la section « Réappro / boutons rapides » des Réglages (Lot 2) : configuration du nombre
 * (1-5) et des montants des boutons rapides affichés par [com.rebuildit.prestaflow.ui.products.StockReplenishScreen].
 * Lot 3 : ajoute le toggle « Son au scan ».
 *
 * Même pattern que [DashboardPrefsViewModel] : simple relais vers
 * [StockReplenishPreferencesRepository], la normalisation (bornage 1-5, entiers > 0, défaut) étant
 * appliquée côté repository (cf. [com.rebuildit.prestaflow.domain.products.model.normalizeQuickAddAmounts]).
 */
@HiltViewModel
class StockReplenishPrefsViewModel
    @Inject
    constructor(
        private val repository: StockReplenishPreferencesRepository,
    ) : ViewModel() {
        val quickAddAmounts: StateFlow<List<Int>> =
            repository.quickAddAmounts
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = DEFAULT_QUICK_ADD_AMOUNTS,
                )

        /** Défaut activé tant que rien n'est chargé (cf. [StockReplenishPreferencesRepository.soundOnScan]). */
        val soundOnScan: StateFlow<Boolean> =
            repository.soundOnScan
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = true,
                )

        fun setQuickAddAmounts(amounts: List<Int>) {
            viewModelScope.launch {
                repository.setQuickAddAmounts(amounts)
            }
        }

        fun setSoundOnScan(enabled: Boolean) {
            viewModelScope.launch {
                repository.setSoundOnScan(enabled)
            }
        }
    }
