package com.rebuildit.prestaflow.data.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.rebuildit.prestaflow.domain.language.LanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation de [LanguageRepository] sur l'API AndroidX per-app language.
 *
 * La persistance n'est PAS gérée ici : `AppCompatDelegate.setApplicationLocales` la délègue déjà —
 * au `LocaleManager` de la plateforme sur API 33+, ou à une SharedPreferences interne à AppCompat
 * sur API < 33 (activée par le service `AppLocalesMetadataHolderService` + le méta-data
 * `autoStoreLocales` déclarés dans le manifeste). Ce repository se contente d'exposer l'état
 * courant en [Flow] pour les ViewModels et de relayer les changements à AppCompat.
 *
 * Fonctionne avec une `ComponentActivity` classique (pas besoin d'`AppCompatActivity`) : AppCompat
 * enregistre lui-même, au chargement de la lib, un `Application.ActivityLifecycleCallbacks` qui
 * recrée l'Activity courante quand la locale change — la nouvelle langue s'applique donc
 * immédiatement, sans redémarrage de process.
 */
@Singleton
class LanguageRepositoryImpl
    @Inject
    constructor() : LanguageRepository {
        private val currentTag = MutableStateFlow(readCurrentTag())

        override val currentLanguageTag: Flow<String?> = currentTag

        override fun setLanguage(tag: String?) {
            val locales =
                if (tag == null) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(tag)
                }
            AppCompatDelegate.setApplicationLocales(locales)
            currentTag.value = tag
        }

        companion object {
            private fun readCurrentTag(): String? {
                val locales = AppCompatDelegate.getApplicationLocales()
                return if (locales.isEmpty) null else locales[0]?.toLanguageTag()
            }
        }
    }
