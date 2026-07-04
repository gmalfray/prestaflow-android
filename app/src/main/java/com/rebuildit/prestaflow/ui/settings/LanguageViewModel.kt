package com.rebuildit.prestaflow.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rebuildit.prestaflow.domain.language.AppLanguage
import com.rebuildit.prestaflow.domain.language.LanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Pilote la section « Langue » des Réglages : force ou non une langue pour l'app, indépendamment
 * de la langue système, via [LanguageRepository] (API AndroidX per-app language).
 *
 * [currentLanguage] vaut `null` en mode « Système (auto) ». Contrairement aux autres préférences de
 * l'écran Réglages, [setLanguage] ne persiste rien lui-même : il délègue à `AppCompatDelegate`, qui
 * gère la persistance ET déclenche la recréation de l'Activity pour appliquer la nouvelle langue
 * immédiatement.
 */
@HiltViewModel
class LanguageViewModel
    @Inject
    constructor(
        private val repository: LanguageRepository,
    ) : ViewModel() {
        val currentLanguage: StateFlow<AppLanguage?> =
            repository.currentLanguageTag
                .map { AppLanguage.fromTag(it) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = AppLanguage.fromTag(null),
                )

        /** `null` = revenir au mode Système (auto). */
        fun setLanguage(language: AppLanguage?) {
            repository.setLanguage(language?.tag)
        }
    }
