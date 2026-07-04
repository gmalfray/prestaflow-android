package com.rebuildit.prestaflow.data.language

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rebuildit.prestaflow.domain.language.LanguageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation de [LanguageRepository] sur l'API per-app language d'Android.
 *
 * ⚠️ On NE peut PAS se reposer uniquement sur `AppCompatDelegate.setApplicationLocales` ici :
 * l'app utilise une `ComponentActivity` (pas une `AppCompatActivity`), donc sur API 33+ AppCompat
 * n'a pas de contexte pour relayer la locale au `LocaleManager` de la plateforme → l'appel est un
 * no-op silencieux (la langue ne change jamais, `cmd locale get-app-locales` reste vide). On appelle
 * donc **directement le `LocaleManager` du framework sur API 33+** (persistance + recréation
 * d'Activity gérées par le système), et on ne garde le chemin AppCompat qu'en fallback pour API < 33.
 */
@Singleton
class LanguageRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : LanguageRepository {
        private val localeManager: LocaleManager?
            get() =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.getSystemService(LocaleManager::class.java)
                } else {
                    null
                }

        private val currentTag = MutableStateFlow(readCurrentTag())

        override val currentLanguageTag: Flow<String?> = currentTag

        override fun setLanguage(tag: String?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                localeManager?.applicationLocales =
                    if (tag == null) {
                        LocaleList.getEmptyLocaleList()
                    } else {
                        LocaleList.forLanguageTags(tag)
                    }
            } else {
                val locales =
                    if (tag == null) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tag)
                    }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            currentTag.value = tag
        }

        private fun readCurrentTag(): String? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = localeManager?.applicationLocales ?: LocaleList.getEmptyLocaleList()
                return if (locales.isEmpty) null else locales[0]?.toLanguageTag()
            }
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) null else locales[0]?.toLanguageTag()
        }
    }
