package com.rebuildit.prestaflow.fakes

import com.rebuildit.prestaflow.domain.products.StockReplenishPreferencesRepository
import com.rebuildit.prestaflow.domain.products.model.DEFAULT_QUICK_ADD_AMOUNTS
import com.rebuildit.prestaflow.domain.products.model.normalizeQuickAddAmounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fake en mémoire de [StockReplenishPreferencesRepository].
 * Applique la même normalisation que l'implémentation DataStore réelle (cf.
 * [com.rebuildit.prestaflow.data.products.StockReplenishPreferencesRepositoryImpl]).
 */
class FakeStockReplenishPreferencesRepository(
    initial: List<Int> = DEFAULT_QUICK_ADD_AMOUNTS,
    initialSoundOnScan: Boolean = true,
) : StockReplenishPreferencesRepository {
    private val _quickAddAmounts = MutableStateFlow(initial)
    private val _soundOnScan = MutableStateFlow(initialSoundOnScan)

    override val quickAddAmounts: Flow<List<Int>> = _quickAddAmounts
    override val soundOnScan: Flow<Boolean> = _soundOnScan

    /** Dernière valeur normalisée persistée par [setQuickAddAmounts]. */
    var stored: List<Int> = initial
        private set

    /** Dernière valeur persistée par [setSoundOnScan]. */
    var storedSoundOnScan: Boolean = initialSoundOnScan
        private set

    override suspend fun setQuickAddAmounts(amounts: List<Int>) {
        val normalized = normalizeQuickAddAmounts(amounts)
        stored = normalized
        _quickAddAmounts.value = normalized
    }

    override suspend fun setSoundOnScan(enabled: Boolean) {
        storedSoundOnScan = enabled
        _soundOnScan.value = enabled
    }

    /** Émet une nouvelle valeur directement (sans passer par [setQuickAddAmounts]), pour les tests. */
    fun emit(amounts: List<Int>) {
        _quickAddAmounts.value = amounts
    }

    /** Émet une nouvelle valeur directement (sans passer par [setSoundOnScan]), pour les tests. */
    fun emitSoundOnScan(enabled: Boolean) {
        _soundOnScan.value = enabled
    }
}
