package com.rebuildit.prestaflow.ui.sav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lot socle : [SavViewModel] démarre en état vide (pas de logique métier câblée, cf. [SavUiState]
 * et étude `rebuild-it/docs/app-avis-sav.md`). Ce test fige ce comportement volontaire — un
 * chargement qui ne se terminerait jamais serait pire qu'un état vide honnête.
 */
class SavViewModelTest {
    @Test
    fun `l etat initial est vide, pas un chargement sans fin`() {
        val viewModel = SavViewModel()

        assertEquals(SavUiState.Empty, viewModel.uiState.value)
    }
}
