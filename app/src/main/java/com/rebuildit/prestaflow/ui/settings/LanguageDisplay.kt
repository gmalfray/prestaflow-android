package com.rebuildit.prestaflow.ui.settings

import androidx.annotation.StringRes
import com.rebuildit.prestaflow.R
import com.rebuildit.prestaflow.domain.language.AppLanguage

/**
 * Libellé affiché d'une langue — toujours son nom natif (« Deutsch », « Español »…), jamais
 * traduit dans la langue courante de l'app, comme le veut la convention des sélecteurs de langue.
 */
@StringRes
fun AppLanguage.displayNameRes(): Int =
    when (this) {
        AppLanguage.FRENCH -> R.string.language_fr
        AppLanguage.ENGLISH -> R.string.language_en
        AppLanguage.SPANISH -> R.string.language_es
        AppLanguage.GERMAN -> R.string.language_de
        AppLanguage.ITALIAN -> R.string.language_it
        AppLanguage.PORTUGUESE -> R.string.language_pt
        AppLanguage.DUTCH -> R.string.language_nl
    }
