package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.language.LanguageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [LanguageRepository] — ne dépend pas d'`AppCompatDelegate` (Android),
 * contrairement à [com.rebuildit.prestaflow.data.language.LanguageRepositoryImpl].
 */
class FakeLanguageRepository(
    initial: String? = null,
) : LanguageRepository {
    private val _currentLanguageTag = MutableStateFlow(initial)

    override val currentLanguageTag: Flow<String?> = _currentLanguageTag

    /** Dernier tag transmis à [setLanguage] (pour vérifier l'appel côté test). */
    var lastSetTag: String? = initial
        private set

    override fun setLanguage(tag: String?) {
        lastSetTag = tag
        _currentLanguageTag.value = tag
    }

    /** Émet un nouveau tag directement (sans passer par [setLanguage]), pour les tests. */
    fun emit(tag: String?) {
        _currentLanguageTag.value = tag
    }
}
