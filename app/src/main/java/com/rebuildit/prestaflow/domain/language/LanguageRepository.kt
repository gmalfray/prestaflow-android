package com.rebuildit.prestaflow.domain.language

import kotlinx.coroutines.flow.Flow

/**
 * Langue forcée par l'utilisateur dans l'app, indépendamment de la langue système — API AndroidX
 * *per-app language* (`androidx.appcompat.app.AppCompatDelegate.setApplicationLocales`).
 *
 * [currentLanguageTag] vaut `null` quand aucune langue n'est forcée (mode « Système / auto ») :
 * l'app suit alors la langue du téléphone via les ressources `values-<locale>/` standard.
 */
interface LanguageRepository {
    val currentLanguageTag: Flow<String?>

    /** `null` = revenir au mode Système (suit la langue du téléphone). */
    fun setLanguage(tag: String?)
}
