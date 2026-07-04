package com.rebuildit.prestaflow.domain.language

/**
 * Langues disponibles pour le sélecteur in-app (Réglages), en plus du mode « Système (auto) »
 * (représenté par `null` côté [LanguageRepository], pas par une valeur de cet enum).
 *
 * [tag] est le tag BCP 47 passé à `LocaleListCompat.forLanguageTags` et comparé à la valeur
 * renvoyée par `AppCompatDelegate.getApplicationLocales()`. Il correspond au suffixe des dossiers
 * de ressources `values-<tag>/` (le français est la langue par défaut, servie depuis `values/` sans
 * dossier dédié).
 */
enum class AppLanguage(val tag: String) {
    FRENCH("fr"),
    ENGLISH("en"),
    SPANISH("es"),
    GERMAN("de"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    DUTCH("nl"),
    ;

    companion object {
        /** Langue correspondant à un tag BCP 47, ou `null` si absent/inconnu (→ mode Système). */
        fun fromTag(tag: String?): AppLanguage? = tag?.let { t -> values().firstOrNull { it.tag == t } }
    }
}
